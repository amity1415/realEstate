package ae.nest.crm.interaction;

import ae.nest.crm.domain.InteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class InteractionDtos {

    private InteractionDtos() {}

    public record InteractionCreateRequest(
            InteractionType type,
            Instant occurredAt,
            @NotBlank @Size(max = 8000) String text,
            @Size(max = 120) String source) {}

    public record InteractionResponse(
            String id,
            String leadId,
            InteractionType type,
            Instant occurredAt,
            String text,
            String source,
            String authorId,
            Instant createdAt) {}
}
