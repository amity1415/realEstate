package ae.nest.crm.coaching;

import ae.nest.crm.config.AppProperties;
import ae.nest.crm.repo.CoachingRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Database-backed daily spend ledger (sum of stored run costs) plus an in-memory reservation for
 * in-flight requests, so the cap is enforced conservatively even with concurrent calls. A daily
 * budget of 0 or less means "no cap".
 */
@Component
public class UsageLedger {

    private final CoachingRunRepository runs;
    private final AppProperties props;
    private final ConcurrentHashMap<UUID, BigDecimal> inFlight = new ConcurrentHashMap<>();

    public UsageLedger(CoachingRunRepository runs, AppProperties props) {
        this.runs = runs;
        this.props = props;
    }

    public BigDecimal todaySpend(UUID workspaceId) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return runs.sumCostSince(workspaceId, startOfDay);
    }

    /** Reserves an estimated cost if it keeps the workspace within budget. */
    public synchronized boolean tryReserve(UUID workspaceId, BigDecimal estimatedCost) {
        double cap = props.getAi().getDailyBudgetUsd();
        if (cap <= 0) {
            return true; // no cap configured
        }
        BigDecimal projected = todaySpend(workspaceId)
                .add(inFlight.getOrDefault(workspaceId, BigDecimal.ZERO))
                .add(estimatedCost == null ? BigDecimal.ZERO : estimatedCost);
        if (projected.doubleValue() > cap) {
            return false;
        }
        inFlight.merge(workspaceId, estimatedCost == null ? BigDecimal.ZERO : estimatedCost,
                BigDecimal::add);
        return true;
    }

    public void release(UUID workspaceId, BigDecimal estimatedCost) {
        if (estimatedCost == null) {
            return;
        }
        inFlight.merge(workspaceId, estimatedCost.negate(),
                (a, b) -> a.add(b).max(BigDecimal.ZERO));
    }
}
