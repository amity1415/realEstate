package ae.nest.crm.repo;

import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository
        extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    /** Workspace-scoped lookup — the only way services should resolve a lead by id. */
    Optional<Lead> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);

    Optional<Lead> findFirstByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);

    long countByWorkspaceIdAndArchivedAtIsNull(UUID workspaceId);

    long countByWorkspaceIdAndStageAndArchivedAtIsNull(UUID workspaceId, LeadStage stage);

    long countByWorkspaceIdAndSampleTrueAndArchivedAtIsNull(UUID workspaceId);

    /** Active (non-archived) lead counts grouped by stage, in a single query. */
    @Query("select l.stage, count(l) from Lead l where l.workspaceId = :ws and l.archivedAt is null "
            + "group by l.stage")
    List<Object[]> activeStageCounts(@Param("ws") UUID ws);

    /** All active leads across workspaces — for the scheduled re-score job. */
    List<Lead> findByArchivedAtIsNull();
}
