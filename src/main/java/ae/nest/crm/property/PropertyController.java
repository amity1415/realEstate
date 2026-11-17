package ae.nest.crm.property;

import ae.nest.crm.common.PageResponse;
import ae.nest.crm.property.PropertyDtos.InventoryCommitResult;
import ae.nest.crm.property.PropertyDtos.InventoryPreview;
import ae.nest.crm.property.PropertyDtos.PropertyCreateRequest;
import ae.nest.crm.property.PropertyDtos.PropertyDetailResponse;
import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import ae.nest.crm.property.PropertyDtos.PropertyResponse;
import ae.nest.crm.property.PropertyDtos.VerifyRequest;
import ae.nest.crm.security.CurrentUser;
import ae.nest.crm.web.PageSupport;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PropertyController {

    private final PropertyService service;
    private final InventoryImportService inventoryImport;
    private final CurrentUser currentUser;

    public PropertyController(
            PropertyService service,
            InventoryImportService inventoryImport,
            CurrentUser currentUser) {
        this.service = service;
        this.inventoryImport = inventoryImport;
        this.currentUser = currentUser;
    }

    @GetMapping("/properties")
    public PageResponse<PropertyResponse> list(
            @RequestParam(required = false) String emirate,
            @RequestParam(required = false) Integer bedrooms,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean includeSamples,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Pageable pageable = PageSupport.of(page, size, Sort.by(Sort.Direction.ASC, "priceAed"));
        return PageResponse.of(service.list(
                currentUser.workspaceId(), emirate, bedrooms, maxPrice, includeSamples, pageable));
    }

    @PostMapping("/properties")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@Valid @RequestBody PropertyCreateRequest request) {
        return service.create(currentUser.workspaceId(), request);
    }

    @GetMapping("/properties/needs-attention")
    public List<PropertyDetailResponse> needsAttention() {
        return service.needsAttention(currentUser.workspaceId());
    }

    @GetMapping("/properties/{id}")
    public PropertyDetailResponse detail(@PathVariable UUID id) {
        return service.detail(currentUser.workspaceId(), id);
    }

    @PostMapping("/properties/{id}/verify")
    public PropertyDetailResponse verify(
            @PathVariable UUID id, @Valid @RequestBody VerifyRequest request) {
        return service.verify(currentUser.workspaceId(), currentUser.userId(), id, request);
    }

    @GetMapping("/leads/{leadId}/property-matches")
    public List<PropertyMatch> matches(
            @PathVariable UUID leadId, @RequestParam(defaultValue = "3") int limit) {
        int bounded = Math.min(Math.max(limit, 1), 10);
        return service.matchesForLead(currentUser.workspaceId(), leadId, bounded);
    }

    @PostMapping("/imports/properties/preview")
    public InventoryPreview previewInventory(@RequestBody JsonNode payload) {
        return inventoryImport.preview(currentUser.workspaceId(), payload);
    }

    @PostMapping("/imports/properties/commit")
    public InventoryCommitResult commitInventory(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode payload) {
        return inventoryImport.commit(
                currentUser.workspaceId(), currentUser.userId(), idempotencyKey, payload);
    }
}
