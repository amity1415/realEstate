package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Authorised property inventory. Sample rows are excluded from real customer shortlists. */
@Entity
@Table(name = "properties")
@Getter
@Setter
public class Property extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "external_id")
    private String externalId;

    private String developer;
    private String project;

    /** Unit type (e.g. apartment, townhouse, villa). */
    @Column(name = "unit_type")
    private String unitType;

    private String location;
    private String emirate;
    private String community;

    /** Bedrooms; 0 means studio. */
    private Integer bedrooms;

    @Column(name = "price_aed", precision = 18, scale = 2)
    private BigDecimal priceAed;

    @Column(name = "size_sqft", precision = 12, scale = 2)
    private BigDecimal sizeSqft;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> amenities = new ArrayList<>();

    /** Completion status as provided by the source (e.g. ready, off-plan). */
    private String completion;

    /** Listing status (e.g. available, reserved, sold). Rechecked before shortlisting. */
    @Column(name = "listing_status")
    private String listingStatus = "AVAILABLE";

    /** Marks demo/sample inventory. Never used in real customer drafts or documents. */
    @Column(nullable = false)
    private boolean sample = false;
}
