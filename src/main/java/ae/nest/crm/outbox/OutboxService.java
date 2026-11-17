package ae.nest.crm.outbox;

import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.domain.OutboxMessage;
import ae.nest.crm.domain.OutboxStatus;
import ae.nest.crm.outbox.OutboxDtos.EnqueueRequest;
import ae.nest.crm.outbox.OutboxDtos.OutboxResponse;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.OutboxMessageRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enqueues outbound messages into the durable outbox. Enqueuing does not send anything — a worker
 * delivers due messages. A dedupe key makes enqueue idempotent so duplicate clicks never create
 * duplicate deliveries.
 */
@Service
public class OutboxService {

    private final OutboxMessageRepository outbox;
    private final LeadRepository leads;

    public OutboxService(OutboxMessageRepository outbox, LeadRepository leads) {
        this.outbox = outbox;
        this.leads = leads;
    }

    @Transactional
    public OutboxResponse enqueue(UUID workspaceId, UUID createdBy, UUID leadId, EnqueueRequest req) {
        if (leadId != null) {
            leads.findByIdAndWorkspaceId(leadId, workspaceId)
                    .orElseThrow(() -> new NotFoundException("Lead not found"));
        }
        if (req.dedupeKey() != null && !req.dedupeKey().isBlank()) {
            var existing = outbox.findByWorkspaceIdAndDedupeKey(workspaceId, req.dedupeKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        OutboxMessage m = new OutboxMessage();
        m.setWorkspaceId(workspaceId);
        m.setLeadId(leadId);
        m.setChannel(req.channel());
        m.setRecipient(req.recipient().trim());
        m.setSubject(req.subject());
        m.setBody(req.body());
        m.setStatus(OutboxStatus.PENDING);
        m.setScheduledAt(req.scheduledAt() == null ? Instant.now() : req.scheduledAt());
        m.setCreatedBy(createdBy);
        m.setDedupeKey(blankToNull(req.dedupeKey()));
        return toResponse(outbox.save(m));
    }

    @Transactional(readOnly = true)
    public Page<OutboxResponse> list(UUID workspaceId, UUID leadId, Pageable pageable) {
        return outbox.findByLeadIdAndWorkspaceIdOrderByCreatedAtDesc(leadId, workspaceId, pageable)
                .map(OutboxService::toResponse);
    }

    @Transactional
    public OutboxResponse cancel(UUID workspaceId, UUID id) {
        OutboxMessage m = outbox.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (m.getStatus() == OutboxStatus.PENDING) {
            m.setStatus(OutboxStatus.CANCELLED);
            m = outbox.save(m);
        }
        return toResponse(m);
    }

    static OutboxResponse toResponse(OutboxMessage m) {
        return new OutboxResponse(
                m.getId().toString(),
                m.getLeadId() == null ? null : m.getLeadId().toString(),
                m.getChannel().name(),
                m.getRecipient(),
                m.getSubject(),
                m.getBody(),
                m.getStatus().name(),
                m.getAttempts(),
                m.getMaxAttempts(),
                m.getScheduledAt(),
                m.getSentAt(),
                m.isSimulated(),
                m.getErrorMessage(),
                m.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
