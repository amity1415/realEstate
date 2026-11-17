package ae.nest.crm.repo;

import ae.nest.crm.domain.PropertySource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertySourceRepository extends JpaRepository<PropertySource, UUID> {

    Optional<PropertySource> findFirstByPropertyIdAndWorkspaceIdOrderByFetchedAtDesc(
            UUID propertyId, UUID workspaceId);

    List<PropertySource> findByPropertyIdInAndWorkspaceId(List<UUID> propertyIds, UUID workspaceId);
}
