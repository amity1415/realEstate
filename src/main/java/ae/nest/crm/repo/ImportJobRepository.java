package ae.nest.crm.repo;

import ae.nest.crm.domain.ImportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    Optional<ImportJob> findByWorkspaceIdAndIdempotencyKey(UUID workspaceId, String idempotencyKey);
}
