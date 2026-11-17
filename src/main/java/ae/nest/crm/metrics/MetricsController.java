package ae.nest.crm.metrics;

import ae.nest.crm.domain.LeadStage;
import ae.nest.crm.domain.TaskStatus;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.LeadStageEventRepository;
import ae.nest.crm.repo.TaskItemRepository;
import ae.nest.crm.security.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current-snapshot metrics only. These are present-state counts, deliberately NOT historical
 * conversion rates — funnel percentages require defined cohort dates and denominators, which are a
 * later reporting concern (stage-entry events are already captured in {@code lead_stage_events}).
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final LeadRepository leads;
    private final TaskItemRepository tasks;
    private final LeadStageEventRepository stageEvents;
    private final CurrentUser currentUser;

    public MetricsController(
            LeadRepository leads,
            TaskItemRepository tasks,
            LeadStageEventRepository stageEvents,
            CurrentUser currentUser) {
        this.leads = leads;
        this.tasks = tasks;
        this.stageEvents = stageEvents;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> metrics() {
        UUID ws = currentUser.workspaceId();

        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (LeadStage stage : LeadStage.values()) {
            stageCounts.put(stage.name(),
                    leads.countByWorkspaceIdAndStageAndArchivedAtIsNull(ws, stage));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", Instant.now().toString());
        result.put("note", "Current snapshot of active leads by stage. Not a conversion rate.");
        result.put("activeLeads", leads.countByWorkspaceIdAndArchivedAtIsNull(ws));
        result.put("sampleLeads", leads.countByWorkspaceIdAndSampleTrueAndArchivedAtIsNull(ws));
        result.put("currentStageCounts", stageCounts);
        result.put("openTasks", tasks.countByWorkspaceIdAndStatus(ws, TaskStatus.OPEN));
        result.put("overdueTasks",
                tasks.countByWorkspaceIdAndStatusAndDueAtBefore(ws, TaskStatus.OPEN, Instant.now()));
        return result;
    }

    /**
     * Cohort-based conversion over a window. The cohort is the leads that entered the pipeline within
     * [from, to); reached counts are how many of that same cohort ever reached each stage. This is a
     * historical conversion rate with an explicit denominator — distinct from the current snapshot.
     */
    @GetMapping("/conversion")
    public Map<String, Object> conversion(
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        UUID ws = currentUser.workspaceId();
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(90, ChronoUnit.DAYS) : from;

        List<UUID> cohort = stageEvents.cohortLeadIds(ws, start, end);
        int cohortSize = cohort.size();

        Map<String, Object> reached = new LinkedHashMap<>();
        Map<String, Object> rates = new LinkedHashMap<>();
        for (LeadStage stage : LeadStage.values()) {
            long count = cohort.isEmpty() ? 0 : stageEvents.countReached(ws, stage, cohort);
            reached.put(stage.name(), count);
            rates.put(stage.name(), cohortSize == 0 ? null : round((double) count / cohortSize));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("cohortDefinition", "Leads that entered the pipeline within [from, to).");
        result.put("cohortSize", cohortSize);
        result.put("reachedByStage", reached);
        result.put("conversionRateByStage", rates);
        result.put("note", "Historical cohort conversion with an explicit denominator (cohortSize); "
                + "not the current-stage snapshot. Null rates mean an empty cohort.");
        return result;
    }

    private static Double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
