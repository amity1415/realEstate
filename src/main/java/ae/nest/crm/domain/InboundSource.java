package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * An approved inbound lead source (e.g. a website form). Authenticated by a per-source token; only the
 * token hash is stored. The intake endpoint can only create leads, never read data.
 */
@Entity
@Table(name = "inbound_sources")
@Getter
@Setter
public class InboundSource extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    /** SHA-256 hash of the source token. The plaintext token is shown once at creation. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /** Leads from this source are assigned to this member (falls back to the creator). */
    @Column(name = "default_owner_id")
    private UUID defaultOwnerId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
