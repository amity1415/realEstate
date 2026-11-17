package ae.nest.crm.repo;

import ae.nest.crm.domain.TaskItem;
import ae.nest.crm.domain.TaskStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskItemRepository extends JpaRepository<TaskItem, UUID> {

    Optional<TaskItem> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<TaskItem> findByWorkspaceIdAndStatus(UUID workspaceId, TaskStatus status, Pageable pageable);

    Page<TaskItem> findByWorkspaceIdAndStatusAndDueAtBefore(
            UUID workspaceId, TaskStatus status, Instant dueBefore, Pageable pageable);

    Page<TaskItem> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<TaskItem> findByLeadIdAndWorkspaceId(UUID leadId, UUID workspaceId, Pageable pageable);

    long countByWorkspaceIdAndStatus(UUID workspaceId, TaskStatus status);

    long countByWorkspaceIdAndStatusAndDueAtBefore(
            UUID workspaceId, TaskStatus status, Instant dueBefore);

    /** Due open tasks across all workspaces, for the reminder worker. */
    java.util.List<TaskItem> findByStatusAndDueAtLessThanEqual(
            TaskStatus status, Instant time, org.springframework.data.domain.Pageable pageable);
}
