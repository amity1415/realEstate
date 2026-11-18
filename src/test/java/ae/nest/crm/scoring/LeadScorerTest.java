package ae.nest.crm.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import ae.nest.crm.domain.LeadSegment;
import ae.nest.crm.domain.LeadStage;
import ae.nest.crm.scoring.LeadScorer.ScoreResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/** Unit tests for the transparent scorer — no Spring, no database. */
class LeadScorerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void engagedQualifiedLeadWithFitScoresHot() {
        ScoreResult r = LeadScorer.score(
                LeadStage.VIEWING, true, true, true, true, 4,
                NOW.minus(1, ChronoUnit.DAYS), NOW);

        assertThat(r.segment()).isEqualTo(LeadSegment.HOT);
        assertThat(r.score()).isGreaterThanOrEqualTo(70);
        // Top factors are returned, highest-weighted first, capped at three.
        assertThat(r.factors()).hasSize(3);
        assertThat(r.factors().get(0).points())
                .isGreaterThanOrEqualTo(r.factors().get(1).points());
    }

    @Test
    void coldLeadNoEngagementNoFitStaysCold() {
        ScoreResult r = LeadScorer.score(
                LeadStage.NEW, false, false, false, false, 0, null, NOW);

        assertThat(r.segment()).isEqualTo(LeadSegment.COLD);
        assertThat(r.score()).isZero();
        assertThat(r.factors()).isEmpty();
    }

    @Test
    void staleContactBeyondNinetyDaysAddsNoEngagementPoints() {
        ScoreResult recent = LeadScorer.score(
                LeadStage.NEW, false, false, false, false, 3,
                NOW.minus(2, ChronoUnit.DAYS), NOW);
        ScoreResult stale = LeadScorer.score(
                LeadStage.NEW, false, false, false, false, 3,
                NOW.minus(200, ChronoUnit.DAYS), NOW);

        assertThat(recent.score()).isGreaterThan(0);
        assertThat(stale.score()).isZero();
    }

    @Test
    void wonLeadIsCappedSoItDoesNotShowAsAPotentialClient() {
        ScoreResult r = LeadScorer.score(
                LeadStage.WON, true, true, true, true, 5,
                NOW.minus(1, ChronoUnit.DAYS), NOW);

        assertThat(r.score()).isLessThanOrEqualTo(15);
        assertThat(r.segment()).isEqualTo(LeadSegment.COLD);
    }

    @Test
    void recencyDecaysScoreAsDaysPass() {
        ScoreResult today = LeadScorer.score(
                LeadStage.CONTACTED, true, false, false, false, 2,
                NOW.minus(1, ChronoUnit.DAYS), NOW);
        ScoreResult lastMonth = LeadScorer.score(
                LeadStage.CONTACTED, true, false, false, false, 2,
                NOW.minus(25, ChronoUnit.DAYS), NOW);

        assertThat(today.score()).isGreaterThan(lastMonth.score());
    }
}
