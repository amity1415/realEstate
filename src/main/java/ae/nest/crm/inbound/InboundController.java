package ae.nest.crm.inbound;

import ae.nest.crm.inbound.InboundDtos.CreateSourceRequest;
import ae.nest.crm.inbound.InboundDtos.CreatedSourceResponse;
import ae.nest.crm.inbound.InboundDtos.InboundAck;
import ae.nest.crm.inbound.InboundDtos.InboundLeadRequest;
import ae.nest.crm.inbound.InboundDtos.SourceResponse;
import ae.nest.crm.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InboundController {

    private final InboundService service;
    private final CurrentUser currentUser;

    public InboundController(InboundService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    // --- management (owner/admin) ---

    @PostMapping("/inbound-sources")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CreatedSourceResponse createSource(@Valid @RequestBody CreateSourceRequest request) {
        return service.createSource(currentUser.workspaceId(), currentUser.userId(), request);
    }

    @GetMapping("/inbound-sources")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<SourceResponse> listSources() {
        return service.list(currentUser.workspaceId());
    }

    @PostMapping("/inbound-sources/{id}/deactivate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SourceResponse deactivate(@PathVariable UUID id) {
        return service.setActive(currentUser.workspaceId(), id, false);
    }

    // --- token-authenticated public intake (create-only) ---

    @PostMapping("/inbound/leads")
    public InboundAck intake(
            @RequestHeader(name = "X-Inbound-Token", required = false) String token,
            @Valid @RequestBody InboundLeadRequest request) {
        return service.intake(token, request);
    }
}
