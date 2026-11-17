package ae.nest.crm.notification;

import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.domain.Notification;
import ae.nest.crm.domain.NotificationType;
import ae.nest.crm.repo.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    /** Creates a notification, skipping it if a dedupe key already exists (reliable, once-only). */
    @Transactional
    public Notification create(
            UUID workspaceId, UUID recipientId, NotificationType type, String title, String body,
            UUID leadId, UUID taskId, String dedupeKey) {
        if (dedupeKey != null && repo.existsByWorkspaceIdAndDedupeKey(workspaceId, dedupeKey)) {
            return null;
        }
        Notification n = new Notification();
        n.setWorkspaceId(workspaceId);
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLeadId(leadId);
        n.setTaskId(taskId);
        n.setDedupeKey(dedupeKey);
        try {
            return repo.save(n);
        } catch (DataIntegrityViolationException raceLost) {
            // A concurrent worker created the same dedupe key first; that is fine.
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID recipientId, boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? repo.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId, pageable)
                : repo.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repo.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public void markRead(UUID recipientId, UUID id) {
        Notification n = repo.findByIdAndRecipientId(id, recipientId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            repo.save(n);
        }
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        repo.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId,
                        Pageable.ofSize(500))
                .forEach(n -> {
                    n.setReadAt(Instant.now());
                    repo.save(n);
                });
    }
}
