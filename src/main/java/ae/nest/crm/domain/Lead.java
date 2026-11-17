package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leads")
@Getter
@Setter
public class Lead extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    /** Acquisition source (e.g. referral, website, walk-in). Tracked for conversion analysis. */
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStage stage = LeadStage.NEW;

    /** Free-text purpose (e.g. primary residence, investment). */
    private String purpose;

    @Column(name = "budget_aed", precision = 18, scale = 2)
    private BigDecimal budgetAed;

    /** Required bedrooms; 0 means studio. Null means unspecified. */
    private Integer bedrooms;

    private String emirate;
    private String community;

    @Column(name = "purchase_timeframe")
    private String purchaseTimeframe;

    @Column(name = "stated_intent", length = 2000)
    private String statedIntent;

    /** Marks demo/sample leads so they can be hidden/removed without affecting real leads. */
    @Column(nullable = false)
    private boolean sample = false;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
