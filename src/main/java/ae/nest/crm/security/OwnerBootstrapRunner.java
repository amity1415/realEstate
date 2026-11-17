package ae.nest.crm.security;

import ae.nest.crm.config.AppProperties;
import ae.nest.crm.domain.AppUser;
import ae.nest.crm.domain.UserRole;
import ae.nest.crm.domain.UserStatus;
import ae.nest.crm.domain.Workspace;
import ae.nest.crm.repo.AppUserRepository;
import ae.nest.crm.repo.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * One-time owner bootstrap. If {@code app.owner.bootstrap=true} and there are no users yet, creates
 * a workspace and a single owner account from the configured email/password, hashing the password.
 * The plaintext password is used only to create the hash and is never persisted or logged.
 */
@Component
public class OwnerBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrapRunner.class);

    private final AppProperties props;
    private final AppUserRepository users;
    private final WorkspaceRepository workspaces;
    private final PasswordEncoder passwordEncoder;

    public OwnerBootstrapRunner(
            AppProperties props,
            AppUserRepository users,
            WorkspaceRepository workspaces,
            PasswordEncoder passwordEncoder) {
        this.props = props;
        this.users = users;
        this.workspaces = workspaces;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Owner owner = props.getOwner();
        if (!owner.isBootstrap()) {
            return;
        }
        if (users.count() > 0) {
            log.info("Owner bootstrap skipped: users already exist.");
            return;
        }
        if (!StringUtils.hasText(owner.getEmail()) || !StringUtils.hasText(owner.getPassword())) {
            log.warn("Owner bootstrap requested but APP_OWNER_EMAIL/APP_OWNER_PASSWORD are not set.");
            return;
        }

        Workspace workspace = new Workspace();
        workspace.setName(owner.getWorkspaceName());
        workspace.setTimezone(owner.getWorkspaceTimezone());
        workspace = workspaces.save(workspace);

        AppUser user = new AppUser();
        user.setWorkspaceId(workspace.getId());
        user.setEmail(owner.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(owner.getPassword()));
        user.setRole(UserRole.OWNER);
        user.setStatus(UserStatus.ACTIVE);
        user.setDisplayName("Owner");
        users.save(user);

        log.info("Owner bootstrap complete for workspace '{}' (owner email configured).",
                workspace.getName());
    }
}
