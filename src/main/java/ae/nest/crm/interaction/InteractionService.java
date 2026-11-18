package ae.nest.crm.interaction;

import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.domain.Interaction;
import ae.nest.crm.domain.InteractionType;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.interaction.InteractionDtos.InteractionCreateRequest;
import ae.nest.crm.interaction.InteractionDtos.InteractionResponse;
import ae.nest.crm.repo.InteractionRepository;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.scoring.LeadScoreService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {

    private final InteractionRepository interactions;
    private final LeadRepository leads;
    private final LeadScoreService scoreService;

    public InteractionService(
            InteractionRepository interactions, LeadRepository leads, LeadScoreService scoreService) {
        this.interactions = interactions;
        this.leads = leads;
        this.scoreService = scoreService;
    }

    @Transactional
    public InteractionResponse add(
            UUID workspaceId, UUID authorId, UUID leadId, InteractionCreateRequest req) {
        Lead lead = requireLead(workspaceId, leadId);
        Interaction i = new Interaction();
        i.setLeadId(leadId);
        i.setWorkspaceId(workspaceId);
        i.setType(req.type() == null ? InteractionType.NOTE : req.type());
        i.setOccurredAt(req.occurredAt() == null ? Instant.now() : req.occurredAt());
        i.setText(req.text().trim());
        i.setSource(req.source() == null ? "manual" : req.source());
        i.setAuthorId(authorId);
        InteractionResponse saved = toResponse(interactions.save(i));
        scoreService.recomputeForLead(lead);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<InteractionResponse> list(UUID workspaceId, UUID leadId, Pageable pageable) {
        requireLead(workspaceId, leadId);
        return interactions
                .findByLeadIdAndWorkspaceIdOrderByOccurredAtDesc(leadId, workspaceId, pageable)
                .map(InteractionService::toResponse);
    }

    private Lead requireLead(UUID workspaceId, UUID leadId) {
        return leads.findByIdAndWorkspaceId(leadId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
    }

    private static InteractionResponse toResponse(Interaction i) {
        return new InteractionResponse(
                i.getId().toString(),
                i.getLeadId().toString(),
                i.getType(),
                i.getOccurredAt(),
                i.getText(),
                i.getSource(),
                i.getAuthorId() == null ? null : i.getAuthorId().toString(),
                i.getCreatedAt());
    }
}
