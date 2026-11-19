package ae.nest.crm.lead;

import ae.nest.crm.common.PageResponse;
import ae.nest.crm.domain.LeadSegment;
import ae.nest.crm.domain.LeadStage;
import ae.nest.crm.lead.LeadDtos.HotLeadSummary;
import ae.nest.crm.lead.LeadDtos.InboxLeadSummary;
import ae.nest.crm.lead.LeadDtos.LeadCreateRequest;
import ae.nest.crm.lead.LeadDtos.LeadResponse;
import ae.nest.crm.lead.LeadDtos.LeadSummary;
import ae.nest.crm.lead.LeadDtos.LeadUpdateRequest;
import ae.nest.crm.lead.LeadDtos.StageEventDto;
import ae.nest.crm.security.CurrentUser;
import ae.nest.crm.web.PageSupport;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadService service;
    private final CurrentUser currentUser;

    public LeadController(LeadService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<LeadSummary> list(
            @RequestParam(required = false) LeadStage stage,
            @RequestParam(required = false) Boolean sample,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String emirate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        UUID owner = mine ? currentUser.userId() : ownerId;
        return PageResponse.of(service.list(
                currentUser.workspaceId(), stage, sample, includeArchived, emirate, search, owner,
                pageable));
    }

    @GetMapping("/hot")
    public PageResponse<HotLeadSummary> hot(
            @RequestParam(required = false) LeadSegment segment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.unsorted());
        return PageResponse.of(service.hotLeads(currentUser.workspaceId(), segment, pageable));
    }

    @GetMapping("/inbox")
    public PageResponse<InboxLeadSummary> inbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(service.inbox(currentUser.workspaceId(), pageable));
    }

    @PostMapping("/{id}/assign")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public LeadResponse assign(
            @PathVariable UUID id, @org.springframework.web.bind.annotation.RequestBody
                    ae.nest.crm.user.UserDtos.AssignRequest request) {
        return service.assign(currentUser.workspaceId(), currentUser.userId(), id,
                UUID.fromString(request.userId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse create(@Valid @RequestBody LeadCreateRequest request) {
        return service.create(currentUser.workspaceId(), currentUser.userId(), request);
    }

    @GetMapping("/{id}")
    public LeadResponse get(@PathVariable UUID id) {
        return service.get(currentUser.workspaceId(), id);
    }

    @PatchMapping("/{id}")
    public LeadResponse update(@PathVariable UUID id, @Valid @RequestBody LeadUpdateRequest request) {
        return service.update(currentUser.workspaceId(), currentUser.userId(), id, request);
    }

    @PostMapping("/{id}/archive")
    public LeadResponse archive(@PathVariable UUID id) {
        return service.archive(currentUser.workspaceId(), currentUser.userId(), id);
    }

    @PostMapping("/{id}/restore")
    public LeadResponse restore(@PathVariable UUID id) {
        return service.restore(currentUser.workspaceId(), currentUser.userId(), id);
    }

    @GetMapping("/{id}/stage-history")
    public List<StageEventDto> stageHistory(@PathVariable UUID id) {
        return service.stageHistory(currentUser.workspaceId(), id);
    }
}
