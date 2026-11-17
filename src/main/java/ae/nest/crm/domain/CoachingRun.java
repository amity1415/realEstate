package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A stored coaching result for a lead. Doubles as the usage ledger (token counts + estimated cost).
 * {@code inputLeadVersion} and {@code inputHash} let the UI mark a run "Outdated — regenerate" when
 * the lead has changed since the run was produced.
 */
@Entity
@Table(name = "coaching_runs")
@Getter
@Setter
public class CoachingRun extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    /** Hash of the scoped input (lead version, interaction set, requirement, prompt version, model). */
    @Column(name = "input_hash", nullable = false)
    private String inputHash;

    @Column(name = "input_lead_version", nullable = false)
    private long inputLeadVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoachingStatus status;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    /** Whether the provider was a real LLM (true) or the labelled mock (false). */
    @Column(name = "live_provider", nullable = false)
    private boolean liveProvider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> resultJson;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "estimated_cost_usd", precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "error_message")
    private String errorMessage;
}
