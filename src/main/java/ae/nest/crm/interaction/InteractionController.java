package ae.nest.crm.interaction;

import ae.nest.crm.common.PageResponse;
import ae.nest.crm.interaction.InteractionDtos.InteractionCreateRequest;
import ae.nest.crm.interaction.InteractionDtos.InteractionResponse;
import ae.nest.crm.security.CurrentUser;
import ae.nest.crm.web.PageSupport;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads/{leadId}/interactions")
public class InteractionController {

    private final InteractionService service;
    private final CurrentUser currentUser;

    public InteractionController(InteractionService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InteractionResponse add(
            @PathVariable UUID leadId, @Valid @RequestBody InteractionCreateRequest request) {
        return service.add(currentUser.workspaceId(), currentUser.userId(), leadId, request);
    }

    @GetMapping
    public PageResponse<InteractionResponse> list(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.unsorted());
        return PageResponse.of(service.list(currentUser.workspaceId(), leadId, pageable));
    }
}
