package ae.nest.crm.coaching;

import ae.nest.crm.coaching.CoachingDtos.CoachingRunRequest;
import ae.nest.crm.coaching.CoachingDtos.CoachingRunResponse;
import ae.nest.crm.coaching.CoachingDtos.FeedbackRequest;
import ae.nest.crm.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CoachingController {

    private final CoachingService service;
    private final CurrentUser currentUser;

    public CoachingController(CoachingService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Generate (or return the idempotent/cached) coaching run for a lead. */
    @PostMapping("/leads/{leadId}/coaching-runs")
    public CoachingRunResponse coach(
            @PathVariable UUID leadId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CoachingRunRequest request) {
        String language = request == null ? null : request.language();
        return service.coach(
                currentUser.workspaceId(), currentUser.userId(), leadId, idempotencyKey, language);
    }

    @GetMapping("/leads/{leadId}/coaching-runs")
    public List<CoachingRunResponse> history(@PathVariable UUID leadId) {
        return service.history(currentUser.workspaceId(), leadId);
    }

    @GetMapping("/coaching-runs/{id}")
    public CoachingRunResponse get(@PathVariable UUID id) {
        return service.get(currentUser.workspaceId(), id);
    }

    @PostMapping("/coaching-runs/{id}/feedback")
    public void feedback(@PathVariable UUID id, @Valid @RequestBody FeedbackRequest request) {
        service.feedback(currentUser.workspaceId(), currentUser.userId(), id, request);
    }
}
