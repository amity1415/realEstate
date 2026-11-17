package ae.nest.crm.user;

import ae.nest.crm.domain.UserRole;
import ae.nest.crm.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {}

    public record CreateUserRequest(
            @Email @NotBlank String email,
            @Size(max = 120) String displayName,
            @NotNull UserRole role,
            @NotBlank @Size(min = 10, max = 100) String initialPassword) {}

    public record UpdateUserRequest(UserRole role, UserStatus status, @Size(max = 120) String displayName) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 100) String newPassword) {}

    public record AssignRequest(@NotBlank String userId) {}

    public record UserResponse(
            String id, String email, String displayName, UserRole role, UserStatus status,
            Instant createdAt) {}
}
