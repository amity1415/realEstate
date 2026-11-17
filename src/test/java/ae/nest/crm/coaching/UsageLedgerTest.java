package ae.nest.crm.coaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ae.nest.crm.config.AppProperties;
import ae.nest.crm.repo.CoachingRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsageLedgerTest {

    private final UUID ws = UUID.randomUUID();

    private UsageLedger ledgerWith(double cap, String priorSpend) {
        CoachingRunRepository repo = mock(CoachingRunRepository.class);
        when(repo.sumCostSince(eq(ws), any(Instant.class))).thenReturn(new BigDecimal(priorSpend));
        AppProperties props = new AppProperties();
        props.getAi().setDailyBudgetUsd(cap);
        return new UsageLedger(repo, props);
    }

    @Test
    void allowsWithinCapAndBlocksOverCap() {
        UsageLedger ledger = ledgerWith(10.0, "8");
        assertThat(ledger.tryReserve(ws, new BigDecimal("1"))).isTrue(); // 8 + 1 = 9
        assertThat(ledger.tryReserve(ws, new BigDecimal("2"))).isFalse(); // 9 + 2 = 11 > 10
    }

    @Test
    void noCapWhenBudgetZero() {
        UsageLedger ledger = ledgerWith(0.0, "9999");
        assertThat(ledger.tryReserve(ws, new BigDecimal("100"))).isTrue();
    }

    @Test
    void releaseFreesInFlightReservation() {
        UsageLedger ledger = ledgerWith(10.0, "8");
        assertThat(ledger.tryReserve(ws, new BigDecimal("2"))).isTrue(); // in-flight 2 -> 10
        assertThat(ledger.tryReserve(ws, new BigDecimal("1"))).isFalse(); // 8+2+1 = 11
        ledger.release(ws, new BigDecimal("2"));
        assertThat(ledger.tryReserve(ws, new BigDecimal("1"))).isTrue(); // 8+0+1 = 9
    }
}
