package ae.nest.crm.user;

import ae.nest.crm.security.CurrentUser;
import ae.nest.crm.user.UserDtos.ChangePasswordRequest;
import ae.nest.crm.user.UserDtos.CreateUserRequest;
import ae.nest.crm.user.UserDtos.UpdateUserRequest;
import ae.nest.crm.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;
    private final CurrentUser currentUser;

    public UserController(UserService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Any member can see the team roster (for assignment dropdowns). */
    @GetMapping
    public List<UserResponse> list() {
        return service.list(currentUser.workspaceId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(currentUser.workspaceId(), currentUser.userId(), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(currentUser.workspaceId(), currentUser.userId(), id, request);
    }

    /** Any authenticated user may change their own password. */
    @PostMapping("/me/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(currentUser.userId(), request);
    }
}
