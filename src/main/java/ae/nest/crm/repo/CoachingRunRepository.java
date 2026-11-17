package ae.nest.crm.repo;

import ae.nest.crm.domain.CoachingRun;
import ae.nest.crm.domain.CoachingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoachingRunRepository extends JpaRepository<CoachingRun, UUID> {

    Optional<CoachingRun> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<CoachingRun> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);

    List<CoachingRun> findByWorkspaceIdAndLeadIdAndInputHashAndStatus(
            UUID workspaceId, UUID leadId, String inputHash, CoachingStatus status, Pageable pageable);

    List<CoachingRun> findByLeadIdAndWorkspaceIdOrderByCreatedAtDesc(UUID leadId, UUID workspaceId);

    @Query("select coalesce(sum(r.estimatedCostUsd), 0) from CoachingRun r "
            + "where r.workspaceId = :workspaceId and r.createdAt >= :since")
    java.math.BigDecimal sumCostSince(
            @Param("workspaceId") UUID workspaceId, @Param("since") Instant since);
}
