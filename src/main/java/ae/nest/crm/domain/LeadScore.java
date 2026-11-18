package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A lead's intelligence score, kept separate from the lead so re-scoring never bumps its version. */
@Entity
@Table(name = "lead_scores")
@Getter
@Setter
public class LeadScore extends BaseEntity {

    @Column(name = "lead_id", nullable = false, unique = true)
    private UUID leadId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadSegment segment;

    /** Top contributing factors, e.g. [{"factor":"Contacted this week","points":18}]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> factors;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
