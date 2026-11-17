package ae.nest.crm.notification;

import ae.nest.crm.common.PageResponse;
import ae.nest.crm.domain.Notification;
import ae.nest.crm.security.CurrentUser;
import ae.nest.crm.web.PageSupport;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record NotificationResponse(
            String id, String type, String title, String body, String leadId, String taskId,
            Instant readAt, Instant createdAt) {}

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.unsorted());
        return PageResponse.of(
                service.list(currentUser.userId(), unreadOnly, pageable).map(this::toResponse));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", service.unreadCount(currentUser.userId()));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {
        service.markRead(currentUser.userId(), id);
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        service.markAllRead(currentUser.userId());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId().toString(),
                n.getType().name(),
                n.getTitle(),
                n.getBody(),
                n.getLeadId() == null ? null : n.getLeadId().toString(),
                n.getTaskId() == null ? null : n.getTaskId().toString(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}
