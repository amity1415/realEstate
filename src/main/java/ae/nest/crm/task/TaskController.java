package ae.nest.crm.task;

import ae.nest.crm.common.PageResponse;
import ae.nest.crm.domain.TaskStatus;
import ae.nest.crm.security.CurrentUser;
import ae.nest.crm.task.TaskDtos.TaskCreateRequest;
import ae.nest.crm.task.TaskDtos.TaskResponse;
import ae.nest.crm.task.TaskDtos.TaskUpdateRequest;
import ae.nest.crm.web.PageSupport;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    private final TaskService service;
    private final CurrentUser currentUser;

    public TaskController(TaskService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/v1/leads/{leadId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(
            @PathVariable UUID leadId, @Valid @RequestBody TaskCreateRequest request) {
        return service.create(currentUser.workspaceId(), currentUser.userId(), leadId, request);
    }

    @PatchMapping("/api/v1/tasks/{id}")
    public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskUpdateRequest request) {
        return service.update(currentUser.workspaceId(), id, request);
    }

    @GetMapping("/api/v1/tasks")
    public PageResponse<TaskResponse> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant dueBefore,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.by(Sort.Direction.ASC, "dueAt"));
        return PageResponse.of(
                service.list(currentUser.workspaceId(), status, dueBefore, leadId, pageable));
    }
}
