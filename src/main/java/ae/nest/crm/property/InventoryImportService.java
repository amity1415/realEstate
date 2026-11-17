package ae.nest.crm.property;

import ae.nest.crm.common.ApiExceptions.BadRequestException;
import ae.nest.crm.domain.ImportJob;
import ae.nest.crm.domain.Property;
import ae.nest.crm.domain.PropertySource;
import ae.nest.crm.property.PropertyDtos.ImportDuplicate;
import ae.nest.crm.property.PropertyDtos.ImportRowError;
import ae.nest.crm.property.PropertyDtos.InventoryCommitResult;
import ae.nest.crm.property.PropertyDtos.InventoryPreview;
import ae.nest.crm.repo.ImportJobRepository;
import ae.nest.crm.repo.PropertyRepository;
import ae.nest.crm.repo.PropertySourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports authorised property inventory with preview, validation and dedup by external id. Each
 * imported property records a source (approved source, owner, permission, fetched time) and is left
 * UNVERIFIED — imported fields are never treated as verified until a human verifies them. Commit is
 * idempotent by (workspace, idempotency key) and transactional (rollback on failure).
 */
@Service
public class InventoryImportService {

    private final PropertyRepository properties;
    private final PropertySourceRepository sources;
    private final ImportJobRepository importJobs;

    public InventoryImportService(
            PropertyRepository properties,
            PropertySourceRepository sources,
            ImportJobRepository importJobs) {
        this.properties = properties;
        this.sources = sources;
        this.importJobs = importJobs;
    }

    private record Parsed(int index, Property property, String externalId,
            String approvedSource, String sourceOwner, String permission, String error) {
        boolean valid() {
            return error == null;
        }
    }

    @Transactional(readOnly = true)
    public InventoryPreview preview(UUID workspaceId, JsonNode payload) {
        List<Parsed> rows = parse(workspaceId, payload);
        List<ImportRowError> invalid = new ArrayList<>();
        List<ImportDuplicate> updates = new ArrayList<>();
        List<String> sampleProjects = new ArrayList<>();
        int valid = 0;
        int toCreate = 0;
        int sampleRows = 0;

        for (Parsed row : rows) {
            if (!row.valid()) {
                invalid.add(new ImportRowError(row.index(), row.error()));
                continue;
            }
            valid++;
            if (row.property().isSample()) {
                sampleRows++;
            }
            if (row.externalId() != null) {
                var existing = properties.findByWorkspaceIdAndExternalId(workspaceId, row.externalId());
                if (existing.isPresent()) {
                    updates.add(new ImportDuplicate(row.index(), row.externalId(),
                            existing.get().getId().toString()));
                    continue;
                }
            }
            toCreate++;
            if (sampleProjects.size() < 10 && row.property().getProject() != null) {
                sampleProjects.add(row.property().getProject());
            }
        }
        return new InventoryPreview(rows.size(), valid, toCreate, updates.size(), invalid, updates,
                sampleRows, sampleProjects);
    }

    @Transactional
    public InventoryCommitResult commit(UUID workspaceId, UUID actorId, String idempotencyKey,
            JsonNode payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required for inventory commit.");
        }
        var prior = importJobs.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey);
        if (prior.isPresent()) {
            return replay(prior.get());
        }

        List<Parsed> rows = parse(workspaceId, payload);
        int created = 0;
        int updated = 0;
        int invalid = 0;

        for (Parsed row : rows) {
            if (!row.valid()) {
                invalid++;
                continue;
            }
            Property property = null;
            if (row.externalId() != null) {
                property = properties.findByWorkspaceIdAndExternalId(workspaceId, row.externalId())
                        .orElse(null);
            }
            if (property == null) {
                property = properties.save(row.property());
                created++;
            } else {
                copyInto(property, row.property());
                property = properties.save(property);
                updated++;
            }
            // Append a provenance record; UNVERIFIED until a human verifies it.
            PropertySource source = new PropertySource();
            source.setPropertyId(property.getId());
            source.setWorkspaceId(workspaceId);
            source.setApprovedSource(row.approvedSource());
            source.setSourceOwner(row.sourceOwner());
            source.setPermission(row.permission() == null ? "UNKNOWN" : row.permission());
            source.setFetchedAt(Instant.now());
            source.setValidityStatus("UNVERIFIED");
            sources.save(source);
        }

        ImportJob job = new ImportJob();
        job.setWorkspaceId(workspaceId);
        job.setActorId(actorId);
        job.setIdempotencyKey(idempotencyKey);
        job.setOperation("IMPORT_PROPERTIES");
        job.setResult("OK");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("invalid", invalid);
        summary.put("total", rows.size());
        job.setSummary(summary);
        job = importJobs.save(job);

        return new InventoryCommitResult(job.getId().toString(), false, created, updated, invalid,
                rows.size());
    }

    // --- parsing ---

    private List<Parsed> parse(UUID workspaceId, JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new BadRequestException("Empty inventory payload.");
        }
        JsonNode array;
        JsonNode defaults = null;
        if (payload.isArray()) {
            array = payload;
        } else if (payload.isObject() && payload.has("properties")
                && payload.get("properties").isArray()) {
            array = payload.get("properties");
            defaults = payload.get("source");
        } else {
            throw new BadRequestException(
                    "Unrecognised format: expected a JSON array or an object with a 'properties' array.");
        }

        Set<String> seenExternal = new HashSet<>();
        List<Parsed> rows = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            rows.add(parseRow(workspaceId, i, array.get(i), defaults, seenExternal));
        }
        return rows;
    }

    private Parsed parseRow(UUID workspaceId, int index, JsonNode node, JsonNode defaults,
            Set<String> seenExternal) {
        if (node == null || !node.isObject()) {
            return error(index, "Row is not a JSON object");
        }
        String project = text(node, "project");
        String externalId = text(node, "externalId", "external_id");
        if ((project == null || project.isBlank()) && (externalId == null || externalId.isBlank())) {
            return error(index, "Each property needs a project name or an externalId");
        }
        if (externalId != null && !seenExternal.add(externalId.toLowerCase())) {
            return error(index, "Duplicate externalId within the import batch: " + externalId);
        }

        Property p = new Property();
        p.setWorkspaceId(workspaceId);
        p.setExternalId(externalId);
        p.setProject(project);
        p.setDeveloper(text(node, "developer"));
        p.setUnitType(coalesce(text(node, "unitType", "unit_type"), "unit"));
        p.setLocation(text(node, "location"));
        p.setEmirate(text(node, "emirate"));
        p.setCommunity(text(node, "community"));
        p.setCompletion(text(node, "completion"));
        p.setListingStatus(coalesce(text(node, "listingStatus", "listing_status"), "AVAILABLE"));
        p.setSample(bool(node, "sample"));
        try {
            p.setPriceAed(decimal(node, "priceAed", "price_aed", "price"));
            p.setSizeSqft(decimal(node, "sizeSqft", "size_sqft", "size"));
        } catch (NumberFormatException e) {
            return error(index, "Invalid numeric value (price/size)");
        }
        try {
            p.setBedrooms(integer(node, "bedrooms"));
        } catch (NumberFormatException e) {
            return error(index, "Invalid bedrooms value");
        }
        p.setAmenities(stringList(node.get("amenities")));

        String approvedSource = firstNonBlank(text(node, "approvedSource", "approved_source"),
                defaults == null ? null : text(defaults, "approvedSource", "approved_source"));
        String sourceOwner = firstNonBlank(text(node, "sourceOwner", "source_owner"),
                defaults == null ? null : text(defaults, "sourceOwner", "source_owner"));
        String permission = firstNonBlank(text(node, "permission"),
                defaults == null ? null : text(defaults, "permission"));

        return new Parsed(index, p, externalId, approvedSource, sourceOwner, permission, null);
    }

    private void copyInto(Property target, Property src) {
        target.setProject(src.getProject());
        target.setDeveloper(src.getDeveloper());
        target.setUnitType(src.getUnitType());
        target.setLocation(src.getLocation());
        target.setEmirate(src.getEmirate());
        target.setCommunity(src.getCommunity());
        target.setCompletion(src.getCompletion());
        target.setListingStatus(src.getListingStatus());
        target.setSample(src.isSample());
        target.setPriceAed(src.getPriceAed());
        target.setSizeSqft(src.getSizeSqft());
        target.setBedrooms(src.getBedrooms());
        target.setAmenities(src.getAmenities());
    }

    private InventoryCommitResult replay(ImportJob job) {
        Map<String, Object> s = job.getSummary() == null ? Map.of() : job.getSummary();
        return new InventoryCommitResult(job.getId().toString(), true, asInt(s.get("created")),
                asInt(s.get("updated")), asInt(s.get("invalid")), asInt(s.get("total")));
    }

    private static Parsed error(int index, String reason) {
        return new Parsed(index, null, null, null, null, null, reason);
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static String text(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull()) {
                String s = v.asText();
                if (s != null && !s.isBlank()) {
                    return s.trim();
                }
            }
        }
        return null;
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> {
                if (n.isTextual() && !n.asText().isBlank()) {
                    out.add(n.asText().trim());
                }
            });
        }
        return out;
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.asBoolean(false);
    }

    private static BigDecimal decimal(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull()) {
                if (v.isNumber()) {
                    return v.decimalValue();
                }
                String s = v.asText().replace(",", "").trim();
                if (!s.isBlank()) {
                    return new BigDecimal(s);
                }
            }
        }
        return null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isInt()) {
            return v.asInt();
        }
        String s = v.asText().trim();
        return s.isBlank() ? null : Integer.parseInt(s);
    }

    private static String coalesce(String a, String b) {
        return a == null ? b : a;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b == null || b.isBlank()) ? null : b;
    }
}
