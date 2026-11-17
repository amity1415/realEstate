package ae.nest.crm.coaching;

import ae.nest.crm.coaching.CoachingDtos.CoachResult;
import ae.nest.crm.coaching.CoachingDtos.Fact;
import ae.nest.crm.coaching.CoachingDtos.FollowUp;
import ae.nest.crm.coaching.CoachingDtos.Hypothesis;
import ae.nest.crm.coaching.CoachingDtos.IntentSignal;
import ae.nest.crm.coaching.CoachingDtos.NextAction;
import ae.nest.crm.coaching.CoachingDtos.Objection;
import ae.nest.crm.coaching.provider.CoachGeneration;
import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Grounds a provider's proposal before it is stored or shown. Evidence IDs must be valid UUIDs that
 * belong to the scoped interaction set; unsupported facts, hypotheses, objections and intent signals
 * are dropped. Property matches are replaced with the deterministic matches computed in code, so a
 * provider can never introduce or imply a property that is not in authorised inventory.
 */
public final class CoachingValidator {

    private CoachingValidator() {}

    public static CoachResult validate(
            CoachGeneration gen,
            Set<String> scopedInteractionIds,
            List<PropertyMatch> deterministicMatches,
            String language) {

        List<String> warnings = new ArrayList<>(nullSafe(gen.warnings()));

        List<Fact> facts = new ArrayList<>();
        for (Fact f : nullSafe(gen.facts())) {
            List<String> ids = validIds(f.interactionIds(), scopedInteractionIds);
            if (!ids.isEmpty()) {
                facts.add(new Fact(f.text(), ids));
            } else {
                warnings.add("Dropped an unsupported fact with no resolvable evidence.");
            }
        }

        List<Hypothesis> hypotheses = new ArrayList<>();
        for (Hypothesis h : nullSafe(gen.hypotheses())) {
            // Hypotheses are inferences; keep them, but only with evidence IDs that resolve.
            hypotheses.add(new Hypothesis(h.text(), normaliseConfidence(h.confidence()),
                    validIds(h.interactionIds(), scopedInteractionIds)));
        }

        List<Objection> objections = new ArrayList<>();
        for (Objection o : nullSafe(gen.objections())) {
            List<String> ids = validIds(o.interactionIds(), scopedInteractionIds);
            if (o.stated() && ids.isEmpty()) {
                warnings.add("Dropped a 'stated' objection with no resolvable evidence.");
                continue;
            }
            objections.add(new Objection(o.category(), o.stated(), ids));
        }

        List<IntentSignal> intentSignals = new ArrayList<>();
        for (IntentSignal s : nullSafe(gen.intentSignals())) {
            List<String> ids = validIds(s.interactionIds(), scopedInteractionIds);
            if (ids.isEmpty()) {
                warnings.add("Dropped an intent signal with no resolvable evidence.");
                continue;
            }
            intentSignals.add(new IntentSignal(s.text(), ids));
        }

        NextAction nextAction = gen.nextAction();
        if (nextAction == null || isBlank(nextAction.type())) {
            nextAction = new NextAction("CLARIFY_REQUIREMENTS",
                    "Insufficient basis for a specific action; gather more information first.",
                    List.of("What matters most to you in the next property you consider?"));
        }

        String draft = gen.messageDraft();
        if (isBlank(draft)) {
            draft = "Thanks for your time. When you're ready, let me know how I can help and I'll follow "
                    + "up with suitable options.";
            warnings.add("Provider returned no draft; a neutral placeholder was used.");
        }

        FollowUp followUp = gen.followUp();
        if (followUp == null || isBlank(followUp.basis())) {
            followUp = new FollowUp("SUGGESTED", null, "Agree a specific time with the buyer.");
        } else if ("BUYER_AGREED".equalsIgnoreCase(followUp.basis()) && followUp.suggestedAt() == null) {
            // Cannot assert an agreed time without one; downgrade to a suggestion.
            followUp = new FollowUp("SUGGESTED", null,
                    followUp.reason() == null ? "Confirm a specific time with the buyer."
                            : followUp.reason());
            warnings.add("Follow-up marked as buyer-agreed without a time; recorded as a suggestion.");
        }

        List<PropertyMatch> matches = deterministicMatches == null ? List.of() : deterministicMatches;

        return new CoachResult(
                gen.summary(),
                facts,
                hypotheses,
                dedupe(nullSafe(gen.unknowns())),
                objections,
                intentSignals,
                nextAction,
                gen.alternativeApproach(),
                matches,
                draft,
                language,
                followUp,
                dedupe(warnings));
    }

    private static List<String> validIds(List<String> ids, Set<String> scoped) {
        List<String> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            if (id != null && isUuid(id) && scoped.contains(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String normaliseConfidence(String c) {
        if (c == null) {
            return "low";
        }
        String v = c.toLowerCase();
        return (v.equals("low") || v.equals("medium") || v.equals("high")) ? v : "low";
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static List<String> dedupe(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
