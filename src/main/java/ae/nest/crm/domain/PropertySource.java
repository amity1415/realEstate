package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Provenance and freshness for property facts. Used to mark stale/unverified data. */
@Entity
@Table(name = "property_sources")
@Getter
@Setter
public class PropertySource extends BaseEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "approved_source")
    private String approvedSource;

    @Column(name = "document_version")
    private String documentVersion;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    /** e.g. VALID, STALE, UNVERIFIED. */
    @Column(name = "validity_status")
    private String validityStatus = "UNVERIFIED";

    /** Who owns/authorised the data (brokerage, developer, portal with permission). */
    @Column(name = "source_owner")
    private String sourceOwner;

    /** Permission to use the source, e.g. AUTHORISED, PENDING, UNKNOWN. */
    private String permission = "UNKNOWN";
}
