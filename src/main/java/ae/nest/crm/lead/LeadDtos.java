package ae.nest.crm.lead;

import ae.nest.crm.domain.LeadSegment;
import ae.nest.crm.domain.LeadStage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response DTOs for the lead API. */
public final class LeadDtos {

    private LeadDtos() {}

    public record RequirementDto(
            List<String> mustHaveAmenities,
            Map<String, Object> preferences,
            @Size(max = 1000) String provenance) {}

    public record LeadCreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 200) String email,
            @Size(max = 60) String phone,
            @Size(max = 120) String source,
            LeadStage stage,
            @Size(max = 120) String purpose,
            @Min(0) BigDecimal budgetAed,
            @Min(0) Integer bedrooms,
            @Size(max = 120) String emirate,
            @Size(max = 120) String community,
            @Size(max = 120) String purchaseTimeframe,
            @Size(max = 2000) String statedIntent,
            Boolean sample,
            RequirementDto requirement) {}

    /** Update request. {@code version} enables optimistic concurrency; {@code stageReason} is logged. */
    public record LeadUpdateRequest(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 200) String email,
            @Size(max = 60) String phone,
            @Size(max = 120) String source,
            LeadStage stage,
            @Size(max = 120) String purpose,
            @Min(0) BigDecimal budgetAed,
            @Min(0) Integer bedrooms,
            @Size(max = 120) String emirate,
            @Size(max = 120) String community,
            @Size(max = 120) String purchaseTimeframe,
            @Size(max = 2000) String statedIntent,
            @Size(max = 500) String stageReason,
            RequirementDto requirement,
            long version) {}

    public record LeadResponse(
            String id,
            String name,
            String email,
            String phone,
            String source,
            LeadStage stage,
            String purpose,
            BigDecimal budgetAed,
            Integer bedrooms,
            String emirate,
            String community,
            String purchaseTimeframe,
            String statedIntent,
            boolean sample,
            Instant archivedAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            RequirementDto requirement) {}

    public record LeadSummary(
            String id,
            String name,
            String email,
            String phone,
            LeadStage stage,
            String source,
            BigDecimal budgetAed,
            Integer bedrooms,
            String emirate,
            boolean sample,
            Instant archivedAt,
            Instant updatedAt,
            Integer score,
            LeadSegment segment) {}

    /** A high-intent lead for the "Hot leads" view, with the transparent factors behind the score. */
    public record HotLeadSummary(
            String id,
            String name,
            String email,
            String phone,
            LeadStage stage,
            String emirate,
            BigDecimal budgetAed,
            Integer bedrooms,
            int score,
            LeadSegment segment,
            List<Map<String, Object>> factors,
            Instant computedAt,
            Instant updatedAt) {}

    /** A freshly-arrived lead awaiting first response, with where it came from and its score. */
    public record InboxLeadSummary(
            String id,
            String name,
            String email,
            String phone,
            String source,
            String channel,
            String utmCampaign,
            String adId,
            String formId,
            Integer score,
            LeadSegment segment,
            Instant createdAt) {}

    public record StageEventDto(
            String id, LeadStage oldStage, LeadStage newStage, Instant changedAt, String reason) {}
}
