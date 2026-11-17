package ae.nest.crm.coaching;

import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Coaching request/response DTOs and the validated output contract.
 *
 * <p>Facts, hypotheses, objections and intent signals carry evidence (interaction IDs). Property
 * matches are always the deterministic matches computed in code — never whatever a model names.
 */
public final class CoachingDtos {

    private CoachingDtos() {}

    // --- requests ---

    public record CoachingRunRequest(@Size(max = 16) String language) {}

    public record FeedbackRequest(
            Boolean helpful, Boolean edited, @Size(max = 200) String actionTaken,
            @Size(max = 2000) String comment) {}

    // --- output contract ---

    public record Fact(String text, List<String> interactionIds) {}

    public record Hypothesis(String text, String confidence, List<String> interactionIds) {}

    public record Objection(String category, boolean stated, List<String> interactionIds) {}

    public record IntentSignal(String text, List<String> interactionIds) {}

    public record NextAction(String type, String rationale, List<String> questions) {}

    public record FollowUp(String basis, Instant suggestedAt, String reason) {}

    public record CoachResult(
            String summary,
            List<Fact> facts,
            List<Hypothesis> hypotheses,
            List<String> unknowns,
            List<Objection> objections,
            List<IntentSignal> intentSignals,
            NextAction nextAction,
            String alternativeApproach,
            List<PropertyMatch> propertyMatches,
            String messageDraft,
            String language,
            FollowUp followUp,
            List<String> warnings) {}

    // --- responses ---

    public record Usage(Integer inputTokens, Integer outputTokens, BigDecimal estimatedCostUsd) {}

    public record CoachingRunResponse(
            String id,
            String leadId,
            String status,
            String provider,
            String model,
            String promptVersion,
            boolean live,
            boolean mock,
            boolean outdated,
            long inputLeadVersion,
            Instant createdAt,
            Usage usage,
            CoachResult result,
            String errorMessage) {}
}
