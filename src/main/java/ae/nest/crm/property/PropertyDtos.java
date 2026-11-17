package ae.nest.crm.property;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PropertyDtos {

    private PropertyDtos() {}

    public record PropertyCreateRequest(
            @Size(max = 120) String externalId,
            @Size(max = 160) String developer,
            @Size(max = 160) String project,
            @NotBlank @Size(max = 80) String unitType,
            @Size(max = 160) String location,
            @Size(max = 120) String emirate,
            @Size(max = 120) String community,
            @Min(0) Integer bedrooms,
            @Min(0) BigDecimal priceAed,
            @Min(0) BigDecimal sizeSqft,
            List<String> amenities,
            @Size(max = 60) String completion,
            @Size(max = 40) String listingStatus,
            Boolean sample) {}

    public record PropertyResponse(
            String id,
            String externalId,
            String developer,
            String project,
            String unitType,
            String location,
            String emirate,
            String community,
            Integer bedrooms,
            BigDecimal priceAed,
            BigDecimal sizeSqft,
            List<String> amenities,
            String completion,
            String listingStatus,
            boolean sample,
            Instant updatedAt) {}

    /** A deterministic match with transparent fit reasons and trade-offs. */
    public record PropertyMatch(
            PropertyResponse property, int score, List<String> fit, List<String> tradeOffs) {}

    /** Freshness/provenance view for a property, derived from its latest source + the policy. */
    public record FreshnessInfo(
            String status,
            Instant verifiedAt,
            Instant fetchedAt,
            String approvedSource,
            String sourceOwner,
            String permission,
            String validityStatus,
            int freshnessDays) {}

    public record PropertyDetailResponse(PropertyResponse property, FreshnessInfo freshness) {}

    /** Verification request to mark a property's facts as checked against an approved source. */
    public record VerifyRequest(
            @Size(max = 200) String approvedSource,
            @Size(max = 200) String sourceOwner,
            @Size(max = 40) String permission,
            @Size(max = 60) String documentVersion) {}

    // --- authorised inventory import ---

    public record ImportRowError(int index, String reason) {}

    public record ImportDuplicate(int index, String externalId, String matchedPropertyId) {}

    public record InventoryPreview(
            int total,
            int valid,
            int toCreate,
            int toUpdate,
            List<ImportRowError> invalid,
            List<ImportDuplicate> updatesExisting,
            int sampleRows,
            List<String> sampleProjects) {}

    public record InventoryCommitResult(
            String importJobId,
            boolean idempotentReplay,
            int created,
            int updated,
            int invalid,
            int total) {}
}
