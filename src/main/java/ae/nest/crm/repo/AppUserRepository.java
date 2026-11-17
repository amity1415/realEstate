package ae.nest.crm.repo;

import ae.nest.crm.domain.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByWorkspaceId(UUID workspaceId);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    Optional<AppUser> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long count();
}
