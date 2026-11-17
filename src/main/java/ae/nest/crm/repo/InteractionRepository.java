package ae.nest.crm.repo;

import ae.nest.crm.domain.Interaction;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    Page<Interaction> findByLeadIdAndWorkspaceIdOrderByOccurredAtDesc(
            UUID leadId, UUID workspaceId, Pageable pageable);
}
