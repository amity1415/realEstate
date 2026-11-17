package ae.nest.crm.outbox;

import ae.nest.crm.domain.OutboxMessage;
import ae.nest.crm.domain.OutboxStatus;
import ae.nest.crm.repo.OutboxMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox operations, separated so the (potentially slow) delivery call happens OUTSIDE
 * any database transaction. Claiming uses row locks with SKIP LOCKED so replicas never double-deliver.
 */
@Component
public class OutboxTx {

    private final OutboxMessageRepository outbox;

    public OutboxTx(OutboxMessageRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public List<OutboxMessage> claimBatch(int limit) {
        List<OutboxMessage> due = outbox.claimDue(Instant.now(), limit);
        for (OutboxMessage m : due) {
            m.setStatus(OutboxStatus.PROCESSING);
        }
        return outbox.saveAll(due);
    }

    @Transactional
    public void markResult(UUID id, boolean success, boolean simulated, String error) {
        OutboxMessage m = outbox.findById(id).orElse(null);
        if (m == null || m.getStatus() != OutboxStatus.PROCESSING) {
            return;
        }
        m.setAttempts(m.getAttempts() + 1);
        m.setLastAttemptAt(Instant.now());
        if (success) {
            m.setStatus(simulated ? OutboxStatus.SIMULATED_SENT : OutboxStatus.SENT);
            m.setSentAt(Instant.now());
            m.setSimulated(simulated);
            m.setErrorMessage(null);
        } else {
            m.setErrorMessage(error);
            if (m.getAttempts() >= m.getMaxAttempts()) {
                m.setStatus(OutboxStatus.FAILED);
            } else {
                m.setStatus(OutboxStatus.PENDING);
                m.setScheduledAt(Instant.now().plusSeconds(60L * m.getAttempts()));
            }
        }
        outbox.save(m);
    }
}
