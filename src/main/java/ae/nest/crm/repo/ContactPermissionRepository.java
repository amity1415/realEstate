package ae.nest.crm.repo;

import ae.nest.crm.domain.ContactPermission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactPermissionRepository extends JpaRepository<ContactPermission, UUID> {
    List<ContactPermission> findByLeadIdAndWorkspaceId(UUID leadId, UUID workspaceId);
}
