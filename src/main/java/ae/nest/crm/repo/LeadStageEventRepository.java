package ae.nest.crm.repo;

import ae.nest.crm.domain.LeadStage;
import ae.nest.crm.domain.LeadStageEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadStageEventRepository extends JpaRepository<LeadStageEvent, UUID> {

    List<LeadStageEvent> findByLeadIdAndWorkspaceIdOrderByChangedAtAsc(UUID leadId, UUID workspaceId);

    /** Lead ids that entered the pipeline (first stage event) within the window — the cohort. */
    @Query("select distinct e.leadId from LeadStageEvent e "
            + "where e.workspaceId = :ws and e.oldStage is null "
            + "and e.changedAt >= :from and e.changedAt < :to")
    List<UUID> cohortLeadIds(
            @Param("ws") UUID workspaceId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** How many of the given cohort leads ever reached a stage. */
    @Query("select count(distinct e.leadId) from LeadStageEvent e "
            + "where e.workspaceId = :ws and e.newStage = :stage and e.leadId in :ids")
    long countReached(
            @Param("ws") UUID workspaceId,
            @Param("stage") LeadStage stage,
            @Param("ids") List<UUID> ids);
}
