package ae.nest.crm.auth;

import ae.nest.crm.domain.Workspace;
import ae.nest.crm.repo.WorkspaceRepository;
import ae.nest.crm.security.AppUserPrincipal;
import ae.nest.crm.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentUser currentUser;
    private final WorkspaceRepository workspaces;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CurrentUser currentUser,
            WorkspaceRepository workspaces) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.currentUser = currentUser;
        this.workspaces = workspaces;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record MeResponse(
            String userId,
            String email,
            String displayName,
            String role,
            String workspaceId,
            String workspaceName,
            String timezone) {}

    /** Exposes the CSRF token so the SPA can read it before the first authenticated write. */
    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(), request.password()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            // Persist the context to the (JDBC-backed) HTTP session so it survives restarts.
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            return me();
        } catch (org.springframework.security.core.AuthenticationException ex) {
            // Covers bad credentials, disabled/locked accounts, etc. — do not reveal which.
            throw new ae.nest.crm.common.ApiExceptions.BadRequestException("Invalid email or password");
        }
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public MeResponse me() {
        AppUserPrincipal principal = currentUser.principal();
        Workspace ws = workspaces.findById(principal.getWorkspaceId()).orElse(null);
        return new MeResponse(
                principal.getUserId().toString(),
                principal.getUsername(),
                principal.getUsername(),
                principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""),
                principal.getWorkspaceId().toString(),
                ws == null ? null : ws.getName(),
                ws == null ? null : ws.getTimezone());
    }
}
