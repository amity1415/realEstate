package ae.nest.crm.property;

import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.domain.Property;
import ae.nest.crm.domain.PropertySource;
import ae.nest.crm.property.PropertyDtos.FreshnessInfo;
import ae.nest.crm.property.PropertyDtos.PropertyCreateRequest;
import ae.nest.crm.property.PropertyDtos.PropertyDetailResponse;
import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import ae.nest.crm.property.PropertyDtos.PropertyResponse;
import ae.nest.crm.property.PropertyDtos.VerifyRequest;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.LeadRequirementRepository;
import ae.nest.crm.repo.PropertyRepository;
import ae.nest.crm.repo.PropertySourceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {

    /** Up to three eligible matches, per the coaching contract. */
    public static final int DEFAULT_MATCH_LIMIT = 3;

    private final PropertyRepository properties;
    private final PropertySourceRepository sources;
    private final LeadRepository leads;
    private final LeadRequirementRepository requirements;
    private final FreshnessService freshness;

    public PropertyService(
            PropertyRepository properties,
            PropertySourceRepository sources,
            LeadRepository leads,
            LeadRequirementRepository requirements,
            FreshnessService freshness) {
        this.properties = properties;
        this.sources = sources;
        this.leads = leads;
        this.requirements = requirements;
        this.freshness = freshness;
    }

    @Transactional(readOnly = true)
    public Page<PropertyResponse> list(
            UUID workspaceId,
            String emirate,
            Integer bedrooms,
            BigDecimal maxPrice,
            boolean includeSamples,
            Pageable pageable) {
        Specification<Property> spec = Specification.where(
                        PropertySpecifications.inWorkspace(workspaceId))
                .and(PropertySpecifications.emirate(emirate))
                .and(PropertySpecifications.bedrooms(bedrooms))
                .and(PropertySpecifications.maxPrice(maxPrice))
                .and(PropertySpecifications.sampleIncluded(includeSamples));
        return properties.findAll(spec, pageable).map(PropertyMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PropertyDetailResponse detail(UUID workspaceId, UUID id) {
        Property p = requireProperty(workspaceId, id);
        return new PropertyDetailResponse(PropertyMapper.toResponse(p), freshness.toInfo(
                latestSource(workspaceId, id)));
    }

    /** Non-sample properties that are stale or unverified and need re-checking before use. */
    @Transactional(readOnly = true)
    public List<PropertyDetailResponse> needsAttention(UUID workspaceId) {
        List<Property> all = properties.findAll(
                Specification.where(PropertySpecifications.inWorkspace(workspaceId))
                        .and(PropertySpecifications.sampleIncluded(false)));
        Map<UUID, PropertySource> byProperty = latestSources(workspaceId,
                all.stream().map(Property::getId).toList());
        List<PropertyDetailResponse> out = new ArrayList<>();
        for (Property p : all) {
            PropertySource src = byProperty.get(p.getId());
            if (freshness.isStaleOrUnverified(src)) {
                out.add(new PropertyDetailResponse(PropertyMapper.toResponse(p),
                        freshness.toInfo(src)));
            }
        }
        return out;
    }

    @Transactional
    public PropertyResponse create(UUID workspaceId, PropertyCreateRequest req) {
        return PropertyMapper.toResponse(properties.save(fromRequest(workspaceId, req)));
    }

    /** Mark a property's facts as verified against an approved source (sets it VERIFIED_FRESH). */
    @Transactional
    public PropertyDetailResponse verify(
            UUID workspaceId, UUID verifierId, UUID id, VerifyRequest req) {
        Property p = requireProperty(workspaceId, id);
        PropertySource src = latestSource(workspaceId, id);
        if (src == null) {
            src = new PropertySource();
            src.setPropertyId(p.getId());
            src.setWorkspaceId(workspaceId);
            src.setFetchedAt(Instant.now());
        }
        if (req.approvedSource() != null) {
            src.setApprovedSource(req.approvedSource());
        }
        if (req.sourceOwner() != null) {
            src.setSourceOwner(req.sourceOwner());
        }
        if (req.permission() != null) {
            src.setPermission(req.permission());
        }
        if (req.documentVersion() != null) {
            src.setDocumentVersion(req.documentVersion());
        }
        src.setVerifiedAt(Instant.now());
        src.setVerifiedBy(verifierId);
        src.setValidityStatus("VALID");
        sources.save(src);
        return new PropertyDetailResponse(PropertyMapper.toResponse(p), freshness.toInfo(src));
    }

    /**
     * Deterministic matches for a lead, re-reading current inventory and enforcing hard constraints
     * in code. Each match is annotated with a freshness trade-off when its source is stale/unverified.
     */
    @Transactional(readOnly = true)
    public List<PropertyMatch> matchesForLead(UUID workspaceId, UUID leadId, int limit) {
        Lead lead = leads.findByIdAndWorkspaceId(leadId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
        LeadRequirement req = requirements.findByLeadId(leadId).orElse(null);
        List<Property> candidates = properties
                .findByWorkspaceIdAndSampleFalseAndListingStatusIgnoreCase(workspaceId, "AVAILABLE");
        List<PropertyMatch> matches = PropertyMatcher.match(lead, req, candidates, limit);
        return annotateFreshness(workspaceId, matches);
    }

    private List<PropertyMatch> annotateFreshness(UUID workspaceId, List<PropertyMatch> matches) {
        if (matches.isEmpty()) {
            return matches;
        }
        List<UUID> ids = matches.stream()
                .map(m -> UUID.fromString(m.property().id())).toList();
        Map<UUID, PropertySource> byProperty = latestSources(workspaceId, ids);
        List<PropertyMatch> out = new ArrayList<>();
        for (PropertyMatch m : matches) {
            PropertySource src = byProperty.get(UUID.fromString(m.property().id()));
            String status = freshness.status(src);
            if (!FreshnessService.VERIFIED_FRESH.equals(status)) {
                List<String> tradeOffs = new ArrayList<>(m.tradeOffs());
                tradeOffs.add(status.equals(FreshnessService.STALE)
                        ? "Facts last verified over " + freshness.freshnessDays()
                                + " days ago — re-verify price/availability before sharing."
                        : "Facts not yet verified against an approved source — verify before sharing.");
                out.add(new PropertyMatch(m.property(), m.score(), m.fit(), tradeOffs));
            } else {
                out.add(m);
            }
        }
        return out;
    }

    private PropertySource latestSource(UUID workspaceId, UUID propertyId) {
        return sources.findFirstByPropertyIdAndWorkspaceIdOrderByFetchedAtDesc(propertyId, workspaceId)
                .orElse(null);
    }

    private Map<UUID, PropertySource> latestSources(UUID workspaceId, List<UUID> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Map.of();
        }
        // Keep the newest source per property (list is ordered by fetched_at via the index scan,
        // but we reduce defensively).
        return sources.findByPropertyIdInAndWorkspaceId(propertyIds, workspaceId).stream()
                .collect(Collectors.toMap(
                        PropertySource::getPropertyId,
                        s -> s,
                        (a, b) -> newest(a, b)));
    }

    private static PropertySource newest(PropertySource a, PropertySource b) {
        Instant fa = a.getFetchedAt() == null ? Instant.EPOCH : a.getFetchedAt();
        Instant fb = b.getFetchedAt() == null ? Instant.EPOCH : b.getFetchedAt();
        return fa.isAfter(fb) ? a : b;
    }

    private Property requireProperty(UUID workspaceId, UUID id) {
        return properties.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
    }

    private Property fromRequest(UUID workspaceId, PropertyCreateRequest req) {
        Property p = new Property();
        p.setWorkspaceId(workspaceId);
        p.setExternalId(req.externalId());
        p.setDeveloper(req.developer());
        p.setProject(req.project());
        p.setUnitType(req.unitType());
        p.setLocation(req.location());
        p.setEmirate(req.emirate());
        p.setCommunity(req.community());
        p.setBedrooms(req.bedrooms());
        p.setPriceAed(req.priceAed());
        p.setSizeSqft(req.sizeSqft());
        p.setAmenities(req.amenities() == null ? new ArrayList<>() : new ArrayList<>(req.amenities()));
        p.setCompletion(req.completion());
        p.setListingStatus(req.listingStatus() == null ? "AVAILABLE" : req.listingStatus());
        p.setSample(Boolean.TRUE.equals(req.sample()));
        return p;
    }
}
