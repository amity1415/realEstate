package ae.nest.crm.coaching;

import ae.nest.crm.config.AppProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Simple per-workspace sliding-window rate limiter for coaching requests. */
@Component
public class CoachingRateLimiter {

    private final AppProperties props;
    private final ConcurrentHashMap<UUID, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public CoachingRateLimiter(AppProperties props) {
        this.props = props;
    }

    public synchronized boolean tryAcquire(UUID workspaceId) {
        int max = props.getAi().getMaxPerMinute();
        if (max <= 0) {
            return true;
        }
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(60);
        Deque<Instant> window = windows.computeIfAbsent(workspaceId, k -> new ArrayDeque<>());
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.pollFirst();
        }
        if (window.size() >= max) {
            return false;
        }
        window.addLast(now);
        return true;
    }
}
