package ae.nest.crm.coaching.provider;

import ae.nest.crm.coaching.CoachingDtos.Fact;
import ae.nest.crm.coaching.CoachingDtos.FollowUp;
import ae.nest.crm.coaching.CoachingDtos.Hypothesis;
import ae.nest.crm.coaching.CoachingDtos.IntentSignal;
import ae.nest.crm.coaching.CoachingDtos.NextAction;
import ae.nest.crm.coaching.CoachingDtos.Objection;
import java.math.BigDecimal;
import java.util.List;

/**
 * The provider's proposed soft output. Property matches are intentionally absent — they are supplied
 * deterministically in code, so a provider can never introduce a property that is not in inventory.
 * Everything here is treated as a proposal and passed through validation before it is stored.
 */
public record CoachGeneration(
        String summary,
        List<Fact> facts,
        List<Hypothesis> hypotheses,
        List<String> unknowns,
        List<Objection> objections,
        List<IntentSignal> intentSignals,
        NextAction nextAction,
        String alternativeApproach,
        String messageDraft,
        FollowUp followUp,
        List<String> warnings,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal estimatedCostUsd) {}
