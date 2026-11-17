package ae.nest.crm.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/** Resolves the authenticated principal from the security context for scoping. */
@Component
public final class CurrentUser {

    public AppUserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new UsernameNotFoundException("No authenticated user in context");
        }
        return principal;
    }

    public UUID userId() {
        return principal().getUserId();
    }

    public UUID workspaceId() {
        return principal().getWorkspaceId();
    }
}
