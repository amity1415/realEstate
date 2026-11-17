package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Structured buyer requirements for a lead, stored one row per lead. */
@Entity
@Table(name = "lead_requirements")
@Getter
@Setter
public class LeadRequirement extends BaseEntity {

    @Column(name = "lead_id", nullable = false, unique = true)
    private UUID leadId;

    /** Hard must-have amenities enforced deterministically during matching. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "must_have_amenities", columnDefinition = "jsonb")
    private List<String> mustHaveAmenities = new ArrayList<>();

    /** Soft preferences (free-form key/value), not enforced as hard constraints. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> preferences = new LinkedHashMap<>();

    /** Where a requirement came from (e.g. "stated by buyer", "advisor inference"). */
    @Column(length = 1000)
    private String provenance;
}
