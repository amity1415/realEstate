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
 * Explicit, channel-scoped contact permission for a lead. An old "permission to contact" is NOT
 * permission to upload conversations to an AI provider — that is tracked separately at pilot time.
 */
@Entity
@Table(name = "contact_permissions")
@Getter
@Setter
public class ContactPermission extends BaseEntity {

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactChannel channel;

    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionStatus status = PermissionStatus.UNKNOWN;

    @Column(name = "captured_at")
    private Instant capturedAt;

    private String source;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
}
