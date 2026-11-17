package ae.nest.crm.repo;

import ae.nest.crm.domain.OutboxMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    Optional<OutboxMessage> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<OutboxMessage> findByLeadIdAndWorkspaceIdOrderByCreatedAtDesc(
            UUID leadId, UUID workspaceId, Pageable pageable);

    boolean existsByWorkspaceIdAndDedupeKey(UUID workspaceId, String dedupeKey);

    Optional<OutboxMessage> findByWorkspaceIdAndDedupeKey(UUID workspaceId, String dedupeKey);

    /**
     * Atomically claims due PENDING messages for this worker using row locks with SKIP LOCKED, so
     * multiple replicas never deliver the same message. Caller flips the claimed rows to PROCESSING
     * within the same transaction.
     */
    @Query(value = "SELECT * FROM outbox_messages WHERE status = 'PENDING' AND scheduled_at <= :now "
            + "ORDER BY scheduled_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxMessage> claimDue(@Param("now") Instant now, @Param("limit") int limit);
}
