package ae.nest.crm.notification;

import ae.nest.crm.domain.NotificationType;
import ae.nest.crm.domain.TaskItem;
import ae.nest.crm.domain.TaskStatus;
import ae.nest.crm.repo.TaskItemRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Turns due open tasks into reliable in-app notifications (once-only, via a dedupe key). This is the
 * reminder mechanism — a delivered notification, not just an on-screen "due" marker.
 */
@Service
public class ReminderService {

    private final TaskItemRepository tasks;
    private final NotificationService notifications;

    public ReminderService(TaskItemRepository tasks, NotificationService notifications) {
        this.tasks = tasks;
        this.notifications = notifications;
    }

    /** Creates reminder notifications for due open tasks. Returns how many new notifications were sent. */
    public int runDueReminders() {
        List<TaskItem> due = tasks.findByStatusAndDueAtLessThanEqual(
                TaskStatus.OPEN, Instant.now(), PageRequest.of(0, 200));
        int created = 0;
        for (TaskItem t : due) {
            var n = notifications.create(
                    t.getWorkspaceId(),
                    t.getOwnerId(),
                    NotificationType.TASK_DUE,
                    "Follow-up due",
                    t.getAction(),
                    t.getLeadId(),
                    t.getId(),
                    "task-due:" + t.getId());
            if (n != null) {
                created++;
            }
        }
        return created;
    }
}
