package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A reliable in-app notification delivered to a workspace member (e.g. a due-task reminder). */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String body;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "read_at")
    private Instant readAt;

    /** Dedupe key so the same reminder is never delivered twice. */
    @Column(name = "dedupe_key")
    private String dedupeKey;
}
