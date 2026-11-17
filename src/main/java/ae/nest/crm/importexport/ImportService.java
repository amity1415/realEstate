package ae.nest.crm.importexport;

import ae.nest.crm.common.ApiExceptions.BadRequestException;
import ae.nest.crm.domain.ImportJob;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.domain.LeadStage;
import ae.nest.crm.domain.LeadStageEvent;
import ae.nest.crm.importexport.ImportDtos.CommitResult;
import ae.nest.crm.importexport.ImportDtos.DuplicateRow;
import ae.nest.crm.importexport.ImportDtos.PreviewResult;
import ae.nest.crm.importexport.ImportDtos.RowError;
import ae.nest.crm.repo.ImportJobRepository;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.LeadRequirementRepository;
import ae.nest.crm.repo.LeadStageEventRepository;
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
 * Imports JSON lead backups with preview, validation, deduplication and rollback. Commit is
 * idempotent by (workspace, idempotency key): a repeated commit returns the original result without
 * importing again. The source backup is only read; the user's original browser data is untouched.
 */
@Service
public class ImportService {

    private final LeadRepository leads;
    private final LeadRequirementRepository requirements;
    private final LeadStageEventRepository stageEvents;
    private final ImportJobRepository importJobs;

    public ImportService(
            LeadRepository leads,
            LeadRequirementRepository requirements,
            LeadStageEventRepository stageEvents,
            ImportJobRepository importJobs) {
        this.leads = leads;
        this.requirements = requirements;
        this.stageEvents = stageEvents;
        this.importJobs = importJobs;
    }

    /** A parsed import row with validation outcome. */
    private record Parsed(int index, Lead lead, LeadRequirement requirement, String email,
            String error) {
        boolean valid() {
            return error == null;
        }
    }

    @Transactional(readOnly = true)
    public PreviewResult preview(UUID workspaceId, UUID ownerId, JsonNode payload) {
        List<Parsed> rows = parse(workspaceId, ownerId, payload);

        List<RowError> invalid = new ArrayList<>();
        List<DuplicateRow> dupExisting = new ArrayList<>();
        List<DuplicateRow> dupBatch = new ArrayList<>();
        List<String> sampleNames = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        int toImport = 0;
        int valid = 0;

        for (Parsed row : rows) {
            if (!row.valid()) {
                invalid.add(new RowError(row.index(), row.error()));
                continue;
            }
            valid++;
            String email = row.email();
            if (email != null) {
                var existing = leads.findFirstByWorkspaceIdAndEmailIgnoreCase(workspaceId, email);
                if (existing.isPresent()) {
                    dupExisting.add(new DuplicateRow(
                            row.index(), email, existing.get().getId().toString()));
                    continue;
                }
                if (!seenEmails.add(email.toLowerCase())) {
                    dupBatch.add(new DuplicateRow(row.index(), email, null));
                    continue;
                }
            }
            toImport++;
            if (sampleNames.size() < 10) {
                sampleNames.add(row.lead().getName());
            }
        }

        return new PreviewResult(rows.size(), valid, toImport, invalid, dupExisting, dupBatch,
                sampleNames);
    }

    @Transactional
    public CommitResult commit(UUID workspaceId, UUID ownerId, String idempotencyKey,
            JsonNode payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required for import commit.");
        }
        var prior = importJobs.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey);
        if (prior.isPresent()) {
            return replay(prior.get());
        }

        List<Parsed> rows = parse(workspaceId, ownerId, payload);
        Set<String> seenEmails = new HashSet<>();
        int imported = 0;
        int skippedDuplicates = 0;
        int invalid = 0;

        for (Parsed row : rows) {
            if (!row.valid()) {
                invalid++;
                continue;
            }
            String email = row.email();
            if (email != null) {
                if (leads.findFirstByWorkspaceIdAndEmailIgnoreCase(workspaceId, email).isPresent()
                        || !seenEmails.add(email.toLowerCase())) {
                    skippedDuplicates++;
                    continue;
                }
            }
            Lead saved = leads.save(row.lead());
            LeadRequirement req = row.requirement();
            req.setLeadId(saved.getId());
            requirements.save(req);
            recordInitialStage(saved, ownerId);
            imported++;
        }

        ImportJob job = new ImportJob();
        job.setWorkspaceId(workspaceId);
        job.setActorId(ownerId);
        job.setIdempotencyKey(idempotencyKey);
        job.setOperation("IMPORT_LEADS");
        job.setResult("OK");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("imported", imported);
        summary.put("skippedDuplicates", skippedDuplicates);
        summary.put("invalid", invalid);
        summary.put("total", rows.size());
        job.setSummary(summary);
        job = importJobs.save(job);

        return new CommitResult(job.getId().toString(), false, imported, skippedDuplicates, invalid,
                rows.size());
    }

    // --- parsing ---

    private List<Parsed> parse(UUID workspaceId, UUID ownerId, JsonNode payload) {
        JsonNode array = extractArray(payload);
        List<Parsed> rows = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            rows.add(parseRow(workspaceId, ownerId, i, array.get(i)));
        }
        return rows;
    }

    private JsonNode extractArray(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new BadRequestException("Empty import payload.");
        }
        if (payload.isArray()) {
            return payload;
        }
        if (payload.isObject() && payload.has("leads") && payload.get("leads").isArray()) {
            return payload.get("leads");
        }
        throw new BadRequestException(
                "Unrecognised backup format: expected a JSON array or an object with a 'leads' array.");
    }

    private Parsed parseRow(UUID workspaceId, UUID ownerId, int index, JsonNode node) {
        if (node == null || !node.isObject()) {
            return new Parsed(index, null, null, null, "Row is not a JSON object");
        }
        String name = text(node, "name");
        if (name == null || name.isBlank()) {
            return new Parsed(index, null, null, null, "Missing required field: name");
        }

        Lead lead = new Lead();
        lead.setWorkspaceId(workspaceId);
        lead.setOwnerId(ownerId);
        lead.setName(name.trim());
        lead.setEmail(normaliseEmail(firstText(node, "email")));
        lead.setPhone(text(node, "phone"));
        lead.setSource(firstText(node, "source"));
        lead.setPurpose(firstText(node, "purpose"));
        lead.setEmirate(firstText(node, "emirate"));
        lead.setCommunity(firstText(node, "community"));
        lead.setPurchaseTimeframe(firstText(node, "purchaseTimeframe", "purchase_timeframe"));
        lead.setStatedIntent(firstText(node, "statedIntent", "stated_intent"));
        lead.setSample(bool(node, "sample"));

        try {
            lead.setBudgetAed(decimal(node, "budgetAed", "budget_aed", "budget"));
        } catch (NumberFormatException e) {
            return new Parsed(index, null, null, null, "Invalid budget value");
        }
        try {
            lead.setBedrooms(integer(node, "bedrooms"));
        } catch (NumberFormatException e) {
            return new Parsed(index, null, null, null, "Invalid bedrooms value");
        }

        LeadStage stage = parseStage(firstText(node, "stage"));
        lead.setStage(stage);

        LeadRequirement req = new LeadRequirement();
        JsonNode reqNode = node.get("requirement");
        if (reqNode != null && reqNode.isObject()) {
            JsonNode amenities = reqNode.get("mustHaveAmenities");
            if (amenities == null) {
                amenities = reqNode.get("must_have_amenities");
            }
            if (amenities != null && amenities.isArray()) {
                List<String> list = new ArrayList<>();
                amenities.forEach(a -> {
                    if (a.isTextual()) {
                        list.add(a.asText());
                    }
                });
                req.setMustHaveAmenities(list);
            }
        }

        return new Parsed(index, lead, req, lead.getEmail(), null);
    }

    private void recordInitialStage(Lead lead, UUID ownerId) {
        LeadStageEvent event = new LeadStageEvent();
        event.setLeadId(lead.getId());
        event.setWorkspaceId(lead.getWorkspaceId());
        event.setOldStage(null);
        event.setNewStage(lead.getStage());
        event.setChangedAt(Instant.now());
        event.setAuthorId(ownerId);
        event.setReason("Imported");
        stageEvents.save(event);
    }

    @SuppressWarnings("unchecked")
    private CommitResult replay(ImportJob job) {
        Map<String, Object> s = job.getSummary() == null ? Map.of() : job.getSummary();
        return new CommitResult(
                job.getId().toString(),
                true,
                asInt(s.get("imported")),
                asInt(s.get("skippedDuplicates")),
                asInt(s.get("invalid")),
                asInt(s.get("total")));
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static LeadStage parseStage(String raw) {
        if (raw == null || raw.isBlank()) {
            return LeadStage.NEW;
        }
        try {
            return LeadStage.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LeadStage.NEW;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = text(node, f);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String normaliseEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
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
}
