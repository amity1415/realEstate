package ae.nest.crm.task;

import ae.nest.crm.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TaskDtos {

    private TaskDtos() {}

    public record TaskCreateRequest(
            @NotBlank @Size(max = 500) String action, Instant dueAt) {}

    public record TaskUpdateRequest(
            @Size(max = 500) String action, Instant dueAt, TaskStatus status) {}

    public record TaskResponse(
            String id,
            String leadId,
            String action,
            Instant dueAt,
            TaskStatus status,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {}
}
