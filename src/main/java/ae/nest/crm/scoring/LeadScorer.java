package ae.nest.crm.scoring;

import ae.nest.crm.domain.LeadSegment;
import ae.nest.crm.domain.LeadStage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Transparent, rule-based lead score (0–100) with the top contributing factors — no ML, no black box,
 * and never any protected attribute (nationality, religion, gender, health, inferred wealth). The
 * advisor can always see WHY a lead scored the way it did.
 */
public final class LeadScorer {

    private LeadScorer() {}

    public record Factor(String factor, int points) {}

    public record ScoreResult(int score, LeadSegment segment, List<Factor> factors) {}

    /** Inputs are behavioural + fit signals only. */
    public static ScoreResult score(
            LeadStage stage,
            boolean hasBudget,
            boolean hasBedrooms,
            boolean hasEmirate,
            boolean hasStatedIntent,
            int interactionCount,
            Instant lastInteractionAt,
            Instant now) {

        List<Factor> factors = new ArrayList<>();
        int score = 0;

        // --- Engagement (recency + volume), only if contact is recent enough to matter ---
        if (interactionCount > 0 && lastInteractionAt != null) {
            long days = Math.max(0, Duration.between(lastInteractionAt, now).toDays());
            if (days <= 90) {
                int rec = days <= 3 ? 26 : days <= 7 ? 18 : days <= 30 ? 10 : 4;
                String when = days <= 3 ? "Contacted in last 3 days"
                        : days <= 7 ? "Contacted this week"
                        : days <= 30 ? "Contacted this month" : "Contacted recently";
                factors.add(new Factor(when, rec));
                int vol = Math.min(interactionCount, 5) * 4;
                factors.add(new Factor(interactionCount + " interaction"
                        + (interactionCount == 1 ? "" : "s"), vol));
                score += rec + vol;
            }
        }

        // --- Fit / requirement completeness ---
        if (hasBudget) { score += 8; factors.add(new Factor("Budget known", 8)); }
        if (hasStatedIntent) { score += 6; factors.add(new Factor("Stated intent", 6)); }
        if (hasEmirate) { score += 5; factors.add(new Factor("Location set", 5)); }
        if (hasBedrooms) { score += 4; factors.add(new Factor("Bedrooms set", 4)); }

        // --- Stage ---
        int st = switch (stage) {
            case VIEWING -> 20;
            case QUALIFIED -> 14;
            case CONTACTED -> 6;
            default -> 0;
        };
        if (st > 0) {
            String label = stage == LeadStage.VIEWING ? "In viewing stage"
                    : stage == LeadStage.QUALIFIED ? "Qualified" : "Contacted";
            factors.add(new Factor(label, st));
            score += st;
        }

        // WON (already converted) and LOST are not "potential clients": keep their score low.
        if (stage == LeadStage.WON || stage == LeadStage.LOST) {
            score = Math.min(score, 15);
        }

        score = Math.max(0, Math.min(100, score));

        LeadSegment segment = score >= 70 ? LeadSegment.HOT
                : score >= 45 ? LeadSegment.WARM
                : score >= 25 ? LeadSegment.NURTURE : LeadSegment.COLD;

        factors.sort(Comparator.comparingInt(Factor::points).reversed());
        List<Factor> top = factors.size() > 3 ? factors.subList(0, 3) : factors;

        return new ScoreResult(score, segment, new ArrayList<>(top));
    }
}
