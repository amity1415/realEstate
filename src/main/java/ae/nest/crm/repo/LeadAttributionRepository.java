package ae.nest.crm.repo;

import ae.nest.crm.domain.LeadAttribution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadAttributionRepository extends JpaRepository<LeadAttribution, UUID> {
    Optional<LeadAttribution> findByLeadId(UUID leadId);

    List<LeadAttribution> findByWorkspaceIdAndLeadIdIn(UUID workspaceId, List<UUID> leadIds);
}
