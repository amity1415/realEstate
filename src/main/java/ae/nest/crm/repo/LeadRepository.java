package ae.nest.crm.repo;

import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadStage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository
        extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    /** Workspace-scoped lookup — the only way services should resolve a lead by id. */
    Optional<Lead> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);

    Optional<Lead> findFirstByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email);

    long countByWorkspaceIdAndArchivedAtIsNull(UUID workspaceId);

    long countByWorkspaceIdAndStageAndArchivedAtIsNull(UUID workspaceId, LeadStage stage);

    long countByWorkspaceIdAndSampleTrueAndArchivedAtIsNull(UUID workspaceId);
}
