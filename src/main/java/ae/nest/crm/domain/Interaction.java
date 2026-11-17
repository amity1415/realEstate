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

/** A recorded conversation/activity with a lead. Cited by evidence IDs in coaching output. */
@Entity
@Table(name = "interactions")
@Getter
@Setter
public class Interaction extends BaseEntity {

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InteractionType type = InteractionType.NOTE;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 8000)
    private String text;

    @Column(name = "author_id")
    private UUID authorId;

    /** Where the interaction originated (e.g. manual, whatsapp, email) for later integrations. */
    private String source;

    /** External message/thread reference for later inbound sync. Untrusted; never executed. */
    @Column(name = "external_reference")
    private String externalReference;
}
