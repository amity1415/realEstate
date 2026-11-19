package ae.nest.crm.lead;

import ae.nest.crm.common.ApiExceptions.ConflictException;
import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.common.AuditService;
import ae.nest.crm.domain.AppUser;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.domain.LeadScore;
import ae.nest.crm.domain.LeadSegment;
import ae.nest.crm.domain.LeadStage;
import ae.nest.crm.domain.LeadStageEvent;
import ae.nest.crm.domain.NotificationType;
import ae.nest.crm.domain.LeadAttribution;
import ae.nest.crm.lead.LeadDtos.HotLeadSummary;
import ae.nest.crm.lead.LeadDtos.InboxLeadSummary;
import ae.nest.crm.lead.LeadDtos.LeadCreateRequest;
import ae.nest.crm.lead.LeadDtos.LeadResponse;
import ae.nest.crm.lead.LeadDtos.LeadSummary;
import ae.nest.crm.lead.LeadDtos.LeadUpdateRequest;
import ae.nest.crm.lead.LeadDtos.RequirementDto;
import ae.nest.crm.lead.LeadDtos.StageEventDto;
import ae.nest.crm.notification.NotificationService;
import ae.nest.crm.repo.AppUserRepository;
import ae.nest.crm.repo.LeadAttributionRepository;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.LeadRequirementRepository;
import ae.nest.crm.repo.LeadScoreRepository;
import ae.nest.crm.repo.LeadStageEventRepository;
import ae.nest.crm.scoring.LeadScoreService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    private final LeadRepository leads;
    private final LeadRequirementRepository requirements;
    private final LeadStageEventRepository stageEvents;
    private final AppUserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final LeadScoreService scoreService;
    private final LeadScoreRepository scores;
    private final LeadAttributionRepository attribution;

    public LeadService(
            LeadRepository leads,
            LeadRequirementRepository requirements,
            LeadStageEventRepository stageEvents,
            AppUserRepository users,
            NotificationService notifications,
            AuditService audit,
            LeadScoreService scoreService,
            LeadScoreRepository scores,
            LeadAttributionRepository attribution) {
        this.leads = leads;
        this.requirements = requirements;
        this.stageEvents = stageEvents;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.scoreService = scoreService;
        this.scores = scores;
        this.attribution = attribution;
    }

    @Transactional(readOnly = true)
    public Page<LeadSummary> list(
            UUID workspaceId,
            LeadStage stage,
            Boolean sample,
            boolean includeArchived,
            String emirate,
            String search,
            UUID ownerId,
            Pageable pageable) {
        Specification<Lead> spec = Specification.where(LeadSpecifications.inWorkspace(workspaceId))
                .and(LeadSpecifications.hasStage(stage))
                .and(LeadSpecifications.sampleIs(sample))
                .and(LeadSpecifications.emirate(emirate))
                .and(LeadSpecifications.ownedBy(ownerId))
                .and(LeadSpecifications.search(search));
        if (!includeArchived) {
            spec = spec.and(LeadSpecifications.archivedIs(false));
        }
        Page<Lead> page = leads.findAll(spec, pageable);
        List<UUID> ids = page.map(Lead::getId).getContent();
        Map<UUID, LeadScore> byLead = scoreService.scoresFor(workspaceId, ids);
        return page.map(lead -> LeadMapper.toSummary(lead, byLead.get(lead.getId())));
    }

    /** Triage inbox: new (unworked) leads, newest first, annotated with channel + campaign + score. */
    @Transactional(readOnly = true)
    public Page<InboxLeadSummary> inbox(UUID workspaceId, Pageable pageable) {
        Page<Lead> page = leads.findByWorkspaceIdAndStageAndArchivedAtIsNull(
                workspaceId, LeadStage.NEW, pageable);
        List<UUID> ids = page.map(Lead::getId).getContent();
        Map<UUID, LeadScore> byScore = scoreService.scoresFor(workspaceId, ids);
        Map<UUID, LeadAttribution> byAttr = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            attribution.findByWorkspaceIdAndLeadIdIn(workspaceId, ids)
                    .forEach(a -> byAttr.put(a.getLeadId(), a));
        }
        return page.map(lead -> LeadMapper.toInboxSummary(
                lead, byScore.get(lead.getId()), byAttr.get(lead.getId())));
    }

    /** Highest-intent leads first, optionally narrowed to one segment, with their scoring factors. */
    @Transactional(readOnly = true)
    public Page<HotLeadSummary> hotLeads(UUID workspaceId, LeadSegment segment, Pageable pageable) {
        Page<LeadScore> page = scores.findHot(workspaceId, segment, pageable);
        Map<UUID, Lead> byId = new java.util.HashMap<>();
        leads.findAllById(page.map(LeadScore::getLeadId).getContent())
                .forEach(l -> byId.put(l.getId(), l));
        return page.map(s -> LeadMapper.toHotSummary(byId.get(s.getLeadId()), s));
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID workspaceId, UUID id) {
        Lead lead = requireLead(workspaceId, id);
        LeadRequirement req = requirements.findByLeadId(lead.getId()).orElse(null);
        return LeadMapper.toResponse(lead, req);
    }

    @Transactional
    public LeadResponse create(UUID workspaceId, UUID ownerId, LeadCreateRequest req) {
        Lead lead = new Lead();
        lead.setWorkspaceId(workspaceId);
        lead.setOwnerId(ownerId);
        applyCommon(lead, req.name(), req.email(), req.phone(), req.source(), req.purpose(),
                req.budgetAed(), req.bedrooms(), req.emirate(), req.community(),
                req.purchaseTimeframe(), req.statedIntent());
        lead.setSample(Boolean.TRUE.equals(req.sample()));
        LeadStage initialStage = req.stage() == null ? LeadStage.NEW : req.stage();
        lead.setStage(initialStage);
        lead = leads.save(lead);

        recordStageChange(lead, ownerId, null, initialStage, "Lead created");
        LeadRequirement saved = upsertRequirement(lead.getId(), req.requirement());
        scoreService.recomputeForLead(lead);

        audit.record(workspaceId, ownerId, "LEAD_CREATE", "OK",
                Map.of("leadId", lead.getId().toString(), "sample", lead.isSample()));
        return LeadMapper.toResponse(lead, saved);
    }

    @Transactional
    public LeadResponse update(UUID workspaceId, UUID authorId, UUID id, LeadUpdateRequest req) {
        Lead lead = requireLead(workspaceId, id);
        if (lead.getVersion() != req.version()) {
            throw new ConflictException(
                    "Lead was modified by another request (expected version " + lead.getVersion()
                            + "). Reload and retry.");
        }
        LeadStage previous = lead.getStage();
        applyCommon(lead, req.name(), req.email(), req.phone(), req.source(), req.purpose(),
                req.budgetAed(), req.bedrooms(), req.emirate(), req.community(),
                req.purchaseTimeframe(), req.statedIntent());

        LeadStage newStage = req.stage() == null ? previous : req.stage();
        boolean stageChanged = newStage != previous;
        lead.setStage(newStage);
        lead = leads.save(lead);

        if (stageChanged) {
            recordStageChange(lead, authorId, previous, newStage,
                    req.stageReason() == null ? "Stage updated" : req.stageReason());
        }
        LeadRequirement saved = upsertRequirement(lead.getId(), req.requirement());
        scoreService.recomputeForLead(lead);

        audit.record(workspaceId, authorId, "LEAD_UPDATE", "OK",
                Map.of("leadId", lead.getId().toString(), "stageChanged", stageChanged));
        return LeadMapper.toResponse(lead, saved);
    }

    @Transactional
    public LeadResponse archive(UUID workspaceId, UUID authorId, UUID id) {
        Lead lead = requireLead(workspaceId, id);
        if (lead.getArchivedAt() == null) {
            lead.setArchivedAt(Instant.now());
            lead = leads.save(lead);
        }
        audit.record(workspaceId, authorId, "LEAD_ARCHIVE", "OK",
                Map.of("leadId", lead.getId().toString()));
        LeadRequirement req = requirements.findByLeadId(lead.getId()).orElse(null);
        return LeadMapper.toResponse(lead, req);
    }

    @Transactional
    public LeadResponse restore(UUID workspaceId, UUID authorId, UUID id) {
        Lead lead = requireLead(workspaceId, id);
        if (lead.getArchivedAt() != null) {
            lead.setArchivedAt(null);
            lead = leads.save(lead);
        }
        audit.record(workspaceId, authorId, "LEAD_RESTORE", "OK",
                Map.of("leadId", lead.getId().toString()));
        LeadRequirement req = requirements.findByLeadId(lead.getId()).orElse(null);
        return LeadMapper.toResponse(lead, req);
    }

    @Transactional
    public LeadResponse assign(UUID workspaceId, UUID actorId, UUID id, UUID assigneeId) {
        Lead lead = requireLead(workspaceId, id);
        AppUser assignee = users.findByIdAndWorkspaceId(assigneeId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Assignee not found in this workspace"));
        lead.setOwnerId(assignee.getId());
        lead = leads.save(lead);
        notifications.create(workspaceId, assignee.getId(), NotificationType.LEAD_ASSIGNED,
                "Lead assigned to you", lead.getName(), lead.getId(), null, null);
        audit.record(workspaceId, actorId, "LEAD_ASSIGN", "OK",
                Map.of("leadId", lead.getId().toString(), "assigneeId", assignee.getId().toString()));
        LeadRequirement req = requirements.findByLeadId(lead.getId()).orElse(null);
        return LeadMapper.toResponse(lead, req);
    }

    @Transactional(readOnly = true)
    public List<StageEventDto> stageHistory(UUID workspaceId, UUID id) {
        requireLead(workspaceId, id);
        return stageEvents.findByLeadIdAndWorkspaceIdOrderByChangedAtAsc(id, workspaceId).stream()
                .map(LeadMapper::toStageEvent)
                .toList();
    }

    // --- internals ---

    private Lead requireLead(UUID workspaceId, UUID id) {
        return leads.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
    }

    private void applyCommon(
            Lead lead, String name, String email, String phone, String source, String purpose,
            java.math.BigDecimal budget, Integer bedrooms, String emirate, String community,
            String timeframe, String statedIntent) {
        lead.setName(name == null ? null : name.trim());
        lead.setEmail(blankToNull(email));
        lead.setPhone(blankToNull(phone));
        lead.setSource(blankToNull(source));
        lead.setPurpose(blankToNull(purpose));
        lead.setBudgetAed(budget);
        lead.setBedrooms(bedrooms);
        lead.setEmirate(blankToNull(emirate));
        lead.setCommunity(blankToNull(community));
        lead.setPurchaseTimeframe(blankToNull(timeframe));
        lead.setStatedIntent(blankToNull(statedIntent));
    }

    private void recordStageChange(
            Lead lead, UUID authorId, LeadStage oldStage, LeadStage newStage, String reason) {
        LeadStageEvent event = new LeadStageEvent();
        event.setLeadId(lead.getId());
        event.setWorkspaceId(lead.getWorkspaceId());
        event.setOldStage(oldStage);
        event.setNewStage(newStage);
        event.setChangedAt(Instant.now());
        event.setAuthorId(authorId);
        event.setReason(reason);
        stageEvents.save(event);
    }

    private LeadRequirement upsertRequirement(UUID leadId, RequirementDto dto) {
        LeadRequirement req = requirements.findByLeadId(leadId).orElseGet(() -> {
            LeadRequirement r = new LeadRequirement();
            r.setLeadId(leadId);
            return r;
        });
        if (dto != null) {
            req.setMustHaveAmenities(
                    dto.mustHaveAmenities() == null ? new ArrayList<>()
                            : new ArrayList<>(dto.mustHaveAmenities()));
            req.setPreferences(dto.preferences() == null ? Map.of() : dto.preferences());
            req.setProvenance(dto.provenance());
        }
        return requirements.save(req);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
