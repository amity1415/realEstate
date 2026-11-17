package ae.nest.crm.coaching;

import static org.assertj.core.api.Assertions.assertThat;

import ae.nest.crm.coaching.CoachingDtos.CoachResult;
import ae.nest.crm.coaching.CoachingDtos.Fact;
import ae.nest.crm.coaching.CoachingDtos.FollowUp;
import ae.nest.crm.coaching.CoachingDtos.Hypothesis;
import ae.nest.crm.coaching.CoachingDtos.IntentSignal;
import ae.nest.crm.coaching.CoachingDtos.Objection;
import ae.nest.crm.coaching.provider.CoachGeneration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoachingValidatorTest {

    private final String validId = UUID.randomUUID().toString();
    private final String unscopedId = UUID.randomUUID().toString();
    private final Set<String> scoped = Set.of(validId);

    private CoachGeneration gen(
            List<Fact> facts, List<Objection> objections, List<IntentSignal> signals,
            FollowUp followUp, String draft) {
        return new CoachGeneration("summary", facts, List.of(new Hypothesis("maybe", "low", List.of(validId))),
                List.of("budget"), objections, signals,
                new ae.nest.crm.coaching.CoachingDtos.NextAction("CLARIFY_REQUIREMENTS", "why", List.of()),
                "alt", draft, followUp, List.of(), 100, 200, java.math.BigDecimal.ZERO);
    }

    @Test
    void dropsFactsAndObjectionsWithoutResolvableEvidence() {
        CoachResult r = CoachingValidator.validate(
                gen(
                        List.of(new Fact("supported", List.of(validId)),
                                new Fact("unsupported", List.of(unscopedId)),
                                new Fact("malformed", List.of("not-a-uuid"))),
                        List.of(new Objection("UPFRONT_PAYMENT", true, List.of()),
                                new Objection("TOTAL_PRICE", true, List.of(validId))),
                        List.of(new IntentSignal("engaged", List.of(unscopedId))),
                        new FollowUp("SUGGESTED", null, "later"),
                        "draft"),
                scoped, List.of(), "en");

        assertThat(r.facts()).extracting(Fact::text).containsExactly("supported");
        assertThat(r.objections()).extracting(Objection::category).containsExactly("TOTAL_PRICE");
        assertThat(r.intentSignals()).isEmpty();
        assertThat(r.warnings()).isNotEmpty();
    }

    @Test
    void downgradesBuyerAgreedFollowUpWithoutTime() {
        CoachResult r = CoachingValidator.validate(
                gen(List.of(), List.of(), List.of(),
                        new FollowUp("BUYER_AGREED", null, "they agreed"), "draft"),
                scoped, List.of(), "en");

        assertThat(r.followUp().basis()).isEqualTo("SUGGESTED");
    }

    @Test
    void suppliesSafeDefaultsForMissingDraft() {
        CoachResult r = CoachingValidator.validate(
                gen(List.of(), List.of(), List.of(), new FollowUp("SUGGESTED", null, "later"), "  "),
                scoped, List.of(), "en");

        assertThat(r.messageDraft()).isNotBlank();
        assertThat(r.nextAction()).isNotNull();
    }
}
