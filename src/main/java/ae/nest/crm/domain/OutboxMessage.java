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

/**
 * A durable outbound message. Creating one does not send anything; a worker delivers due messages
 * through a {@code DeliveryProvider}. With no real channel connected the provider is SIMULATED and
 * marks messages SIMULATED_SENT — never claiming real delivery.
 */
@Entity
@Table(name = "outbox_messages")
@Getter
@Setter
public class OutboxMessage extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactChannel channel;

    @Column(nullable = false)
    private String recipient;

    private String subject;

    @Column(nullable = false, length = 8000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** True when the last delivery was simulated (no real channel connected). */
    @Column(nullable = false)
    private boolean simulated = false;

    /** Dedupe key so duplicate clicks / retries never create duplicate deliveries. */
    @Column(name = "dedupe_key")
    private String dedupeKey;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
