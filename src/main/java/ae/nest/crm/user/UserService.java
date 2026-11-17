package ae.nest.crm.user;

import ae.nest.crm.common.ApiExceptions.BadRequestException;
import ae.nest.crm.common.ApiExceptions.ConflictException;
import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.common.AuditService;
import ae.nest.crm.domain.AppUser;
import ae.nest.crm.domain.UserRole;
import ae.nest.crm.domain.UserStatus;
import ae.nest.crm.repo.AppUserRepository;
import ae.nest.crm.user.UserDtos.ChangePasswordRequest;
import ae.nest.crm.user.UserDtos.CreateUserRequest;
import ae.nest.crm.user.UserDtos.UpdateUserRequest;
import ae.nest.crm.user.UserDtos.UserResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(UUID workspaceId) {
        return users.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .map(UserService::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse create(UUID workspaceId, UUID actorId, CreateUserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with that email already exists.");
        }
        AppUser u = new AppUser();
        u.setWorkspaceId(workspaceId);
        u.setEmail(email);
        u.setDisplayName(req.displayName());
        u.setRole(req.role());
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash(passwordEncoder.encode(req.initialPassword()));
        u = users.save(u);
        audit.record(workspaceId, actorId, "USER_CREATE", "OK",
                Map.of("userId", u.getId().toString(), "role", u.getRole().name()));
        return toResponse(u);
    }

    @Transactional
    public UserResponse update(UUID workspaceId, UUID actorId, UUID id, UpdateUserRequest req) {
        AppUser u = users.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (req.role() != null) {
            u.setRole(req.role());
        }
        if (req.status() != null) {
            u.setStatus(req.status());
        }
        if (req.displayName() != null) {
            u.setDisplayName(req.displayName());
        }
        u = users.save(u);
        audit.record(workspaceId, actorId, "USER_UPDATE", "OK",
                Map.of("userId", u.getId().toString()));
        return toResponse(u);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        AppUser u = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (u.getPasswordHash() == null
                || !passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(u);
    }

    static UserResponse toResponse(AppUser u) {
        return new UserResponse(u.getId().toString(), u.getEmail(), u.getDisplayName(), u.getRole(),
                u.getStatus(), u.getCreatedAt());
    }
}
