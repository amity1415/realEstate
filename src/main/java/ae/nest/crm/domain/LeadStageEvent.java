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

/** Immutable record of a pipeline stage transition, for historical conversion analysis. */
@Entity
@Table(name = "lead_stage_events")
@Getter
@Setter
public class LeadStageEvent extends BaseEntity {

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_stage")
    private LeadStage oldStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_stage", nullable = false)
    private LeadStage newStage;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "author_id")
    private UUID authorId;

    private String reason;
}
