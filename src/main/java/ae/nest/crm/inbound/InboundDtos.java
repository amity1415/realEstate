package ae.nest.crm.inbound;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class InboundDtos {

    private InboundDtos() {}

    public record CreateSourceRequest(
            @NotBlank @Size(max = 120) String name, String defaultOwnerId) {}

    public record SourceResponse(
            String id, String name, boolean active, String defaultOwnerId, Instant createdAt) {}

    /** Returned once on creation; the plaintext token is never stored or shown again. */
    public record CreatedSourceResponse(SourceResponse source, String token) {}

    public record InboundLeadRequest(
            @Size(max = 200) String name,
            @Email @Size(max = 200) String email,
            @Size(max = 60) String phone,
            @Size(max = 2000) String message,
            @Min(0) BigDecimal budgetAed,
            @Min(0) Integer bedrooms,
            @Size(max = 120) String emirate) {}

    public record InboundAck(String status, String leadId, boolean duplicate) {}
}
