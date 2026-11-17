package ae.nest.crm.task;

import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.TaskItem;
import ae.nest.crm.domain.TaskStatus;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.TaskItemRepository;
import ae.nest.crm.task.TaskDtos.TaskCreateRequest;
import ae.nest.crm.task.TaskDtos.TaskResponse;
import ae.nest.crm.task.TaskDtos.TaskUpdateRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskItemRepository tasks;
    private final LeadRepository leads;

    public TaskService(TaskItemRepository tasks, LeadRepository leads) {
        this.tasks = tasks;
        this.leads = leads;
    }

    @Transactional
    public TaskResponse create(UUID workspaceId, UUID ownerId, UUID leadId, TaskCreateRequest req) {
        requireLead(workspaceId, leadId);
        TaskItem t = new TaskItem();
        t.setLeadId(leadId);
        t.setWorkspaceId(workspaceId);
        t.setOwnerId(ownerId);
        t.setAction(req.action().trim());
        t.setDueAt(req.dueAt());
        t.setStatus(TaskStatus.OPEN);
        return toResponse(tasks.save(t));
    }

    @Transactional
    public TaskResponse update(UUID workspaceId, UUID id, TaskUpdateRequest req) {
        TaskItem t = tasks.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (req.action() != null && !req.action().isBlank()) {
            t.setAction(req.action().trim());
        }
        if (req.dueAt() != null) {
            t.setDueAt(req.dueAt());
        }
        if (req.status() != null) {
            t.setStatus(req.status());
            t.setCompletedAt(req.status() == TaskStatus.DONE ? Instant.now() : null);
        }
        return toResponse(tasks.save(t));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(
            UUID workspaceId, TaskStatus status, Instant dueBefore, UUID leadId, Pageable pageable) {
        Page<TaskItem> page;
        if (leadId != null) {
            page = tasks.findByLeadIdAndWorkspaceId(leadId, workspaceId, pageable);
        } else if (status != null && dueBefore != null) {
            page = tasks.findByWorkspaceIdAndStatusAndDueAtBefore(
                    workspaceId, status, dueBefore, pageable);
        } else if (status != null) {
            page = tasks.findByWorkspaceIdAndStatus(workspaceId, status, pageable);
        } else {
            page = tasks.findByWorkspaceId(workspaceId, pageable);
        }
        return page.map(TaskService::toResponse);
    }

    private Lead requireLead(UUID workspaceId, UUID leadId) {
        return leads.findByIdAndWorkspaceId(leadId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
    }

    private static TaskResponse toResponse(TaskItem t) {
        return new TaskResponse(
                t.getId().toString(),
                t.getLeadId().toString(),
                t.getAction(),
                t.getDueAt(),
                t.getStatus(),
                t.getCompletedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
