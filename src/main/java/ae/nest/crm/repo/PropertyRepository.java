package ae.nest.crm.repo;

import ae.nest.crm.domain.Property;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PropertyRepository
        extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {

    Optional<Property> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Property> findByWorkspaceIdAndExternalId(UUID workspaceId, String externalId);

    List<Property> findByWorkspaceIdAndSampleFalseAndListingStatusIgnoreCase(
            UUID workspaceId, String listingStatus);
}
