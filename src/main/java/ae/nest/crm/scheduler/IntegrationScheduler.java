package ae.nest.crm.scheduler;

import ae.nest.crm.notification.ReminderService;
import ae.nest.crm.outbox.OutboxDispatcher;
import ae.nest.crm.scoring.LeadScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled triggers for the durable mechanisms. Disabled in tests (which drive the services directly)
 * via {@code app.scheduling.enabled=false}. The worker logic lives in the services, not here.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IntegrationScheduler {

    private static final Logger log = LoggerFactory.getLogger(IntegrationScheduler.class);

    private final ReminderService reminders;
    private final OutboxDispatcher outbox;
    private final LeadScoreService scores;

    public IntegrationScheduler(
            ReminderService reminders, OutboxDispatcher outbox, LeadScoreService scores) {
        this.reminders = reminders;
        this.outbox = outbox;
        this.scores = scores;
    }

    @Scheduled(fixedDelayString = "${app.scheduling.reminders-ms:60000}", initialDelay = 20000)
    public void reminders() {
        try {
            int created = reminders.runDueReminders();
            if (created > 0) {
                log.info("Reminder worker created {} notification(s).", created);
            }
        } catch (RuntimeException e) {
            log.warn("Reminder worker failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduling.outbox-ms:15000}", initialDelay = 15000)
    public void outbox() {
        try {
            outbox.dispatchDue(50);
        } catch (RuntimeException e) {
            log.warn("Outbox dispatcher failed: {}", e.getMessage());
        }
    }

    /**
     * Full re-score sweep. Per-event recompute keeps a lead fresh on contact, but recency decays with
     * time alone (a lead contacted "this week" becomes "this month"), so a periodic sweep re-grades
     * everyone. Default hourly.
     */
    @Scheduled(fixedDelayString = "${app.scheduling.rescore-ms:3600000}", initialDelay = 45000)
    public void rescore() {
        try {
            scores.recomputeAll();
        } catch (RuntimeException e) {
            log.warn("Lead re-score sweep failed: {}", e.getMessage());
        }
    }
}
