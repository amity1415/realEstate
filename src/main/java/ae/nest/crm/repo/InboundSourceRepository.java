package ae.nest.crm.repo;

import ae.nest.crm.domain.InboundSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundSourceRepository extends JpaRepository<InboundSource, UUID> {

    Optional<InboundSource> findByTokenHashAndActiveTrue(String tokenHash);

    List<InboundSource> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<InboundSource> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
