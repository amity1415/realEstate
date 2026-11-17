package ae.nest.crm.outbox;

import ae.nest.crm.domain.ContactChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class OutboxDtos {

    private OutboxDtos() {}

    public record EnqueueRequest(
            @NotNull ContactChannel channel,
            @NotBlank @Size(max = 320) String recipient,
            @Size(max = 300) String subject,
            @NotBlank @Size(max = 8000) String body,
            Instant scheduledAt,
            @Size(max = 200) String dedupeKey) {}

    public record OutboxResponse(
            String id,
            String leadId,
            String channel,
            String recipient,
            String subject,
            String body,
            String status,
            int attempts,
            int maxAttempts,
            Instant scheduledAt,
            Instant sentAt,
            boolean simulated,
            String errorMessage,
            Instant createdAt) {}
}
