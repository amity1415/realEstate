package ae.nest.crm.outbox;

import ae.nest.crm.common.PageResponse;
import ae.nest.crm.outbox.OutboxDtos.EnqueueRequest;
import ae.nest.crm.outbox.OutboxDtos.OutboxResponse;
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
@RequestMapping("/api/v1")
public class OutboxController {

    private final OutboxService service;
    private final CurrentUser currentUser;

    public OutboxController(OutboxService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/leads/{leadId}/outbox")
    @ResponseStatus(HttpStatus.CREATED)
    public OutboxResponse enqueue(
            @PathVariable UUID leadId, @Valid @RequestBody EnqueueRequest request) {
        return service.enqueue(currentUser.workspaceId(), currentUser.userId(), leadId, request);
    }

    @GetMapping("/leads/{leadId}/outbox")
    public PageResponse<OutboxResponse> list(
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.unsorted());
        return PageResponse.of(service.list(currentUser.workspaceId(), leadId, pageable));
    }

    @PostMapping("/outbox/{id}/cancel")
    public OutboxResponse cancel(@PathVariable UUID id) {
        return service.cancel(currentUser.workspaceId(), id);
    }
}
