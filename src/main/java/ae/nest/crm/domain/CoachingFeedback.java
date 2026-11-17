package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Advisor feedback on a coaching run, kept separate from the AI suggestion itself. */
@Entity
@Table(name = "coaching_feedback")
@Getter
@Setter
public class CoachingFeedback extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "advisor_id", nullable = false)
    private UUID advisorId;

    /** Whether the advisor found it helpful. Null = not stated. */
    private Boolean helpful;

    /** Whether the advisor edited the draft before using it. */
    @Column(nullable = false)
    private boolean edited = false;

    /** What the advisor actually did (free text, e.g. "sent edited message"). */
    @Column(name = "action_taken")
    private String actionTaken;

    @Column(length = 2000)
    private String comment;
}
