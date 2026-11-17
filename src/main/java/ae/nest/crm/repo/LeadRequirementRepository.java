package ae.nest.crm.repo;

import ae.nest.crm.domain.LeadRequirement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRequirementRepository extends JpaRepository<LeadRequirement, UUID> {
    Optional<LeadRequirement> findByLeadId(UUID leadId);
}
