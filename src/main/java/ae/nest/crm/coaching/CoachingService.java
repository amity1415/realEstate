package ae.nest.crm.coaching;

import ae.nest.crm.coaching.CoachingDtos.CoachResult;
import ae.nest.crm.coaching.CoachingDtos.CoachingRunResponse;
import ae.nest.crm.coaching.CoachingDtos.FeedbackRequest;
import ae.nest.crm.coaching.CoachingDtos.Usage;
import ae.nest.crm.coaching.provider.CoachContext;
import ae.nest.crm.coaching.provider.CoachContext.ContextInteraction;
import ae.nest.crm.coaching.provider.CoachGeneration;
import ae.nest.crm.coaching.provider.CoachProvider;
import ae.nest.crm.coaching.provider.CoachProviderFactory;
import ae.nest.crm.coaching.provider.CoachProviderFactory.ProviderChoice;
import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.common.AuditService;
import ae.nest.crm.config.AppProperties;
import ae.nest.crm.domain.CoachingFeedback;
import ae.nest.crm.domain.CoachingRun;
import ae.nest.crm.domain.CoachingStatus;
import ae.nest.crm.domain.Interaction;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import ae.nest.crm.property.PropertyService;
import ae.nest.crm.repo.CoachingFeedbackRepository;
import ae.nest.crm.repo.CoachingRunRepository;
import ae.nest.crm.repo.InteractionRepository;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.LeadRequirementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class CoachingService {

    public static final String PROMPT_VERSION = "coach-v1";
    private static final Logger log = LoggerFactory.getLogger(CoachingService.class);

    private final LeadRepository leads;
    private final LeadRequirementRepository requirements;
    private final InteractionRepository interactions;
    private final PropertyService propertyService;
    private final CoachingRunRepository runs;
    private final CoachingFeedbackRepository feedback;
    private final CoachProviderFactory providerFactory;
    private final UsageLedger usageLedger;
    private final CoachingRateLimiter rateLimiter;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final AppProperties props;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CoachingService(
            LeadRepository leads,
            LeadRequirementRepository requirements,
            InteractionRepository interactions,
            PropertyService propertyService,
            CoachingRunRepository runs,
            CoachingFeedbackRepository feedback,
            CoachProviderFactory providerFactory,
            UsageLedger usageLedger,
            CoachingRateLimiter rateLimiter,
            AuditService audit,
            ObjectMapper objectMapper,
            AppProperties props) {
        this.leads = leads;
        this.requirements = requirements;
        this.interactions = interactions;
        this.propertyService = propertyService;
        this.runs = runs;
        this.feedback = feedback;
        this.providerFactory = providerFactory;
        this.usageLedger = usageLedger;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Produce (or return a cached/idempotent) coaching run for a lead. The provider call runs outside
     * any database transaction, with a timeout and bounded retries; failures never fabricate a result.
     */
    public CoachingRunResponse coach(
            UUID workspaceId, UUID authorId, UUID leadId, String idempotencyKey, String language) {
        Lead lead = leads.findByIdAndWorkspaceId(leadId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
        String lang = (language == null || language.isBlank()) ? "en" : language.trim();

        // Idempotency: a repeated key returns the original run without re-charging.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = runs.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey);
            if (existing.isPresent()) {
                return toResponse(existing.get(), lead);
            }
        }

        ProviderChoice choice = providerFactory.choose();
        CoachProvider provider = choice.provider();
        String model = provider.model();

        // Scoped input assembly (deterministic).
        List<Interaction> recent = interactions
                .findByLeadIdAndWorkspaceIdOrderByOccurredAtDesc(leadId, workspaceId,
                        PageRequest.of(0, props.getAi().getMaxContextInteractions()))
                .getContent();
        LeadRequirement requirement = requirements.findByLeadId(leadId).orElse(null);
        List<PropertyMatch> matches = propertyService.matchesForLead(
                workspaceId, leadId, PropertyService.DEFAULT_MATCH_LIMIT);

        Set<String> scopedIds = new LinkedHashSet<>();
        List<ContextInteraction> ctxInteractions = new ArrayList<>();
        for (Interaction i : recent) {
            scopedIds.add(i.getId().toString());
            ctxInteractions.add(new ContextInteraction(
                    i.getId().toString(), i.getType().name(), i.getOccurredAt(), i.getText()));
        }

        String inputHash = hash(leadId, lead.getVersion(), scopedIds, requirement, model);

        // Cache: an identical scoped input already coached → reuse (no repeat charge).
        var cached = runs.findByWorkspaceIdAndLeadIdAndInputHashAndStatus(
                workspaceId, leadId, inputHash, CoachingStatus.COMPLETED, PageRequest.of(0, 1));
        if (!cached.isEmpty()) {
            return toResponse(cached.get(0), lead);
        }

        // Guards: rate limit then budget. These return a transient UNAVAILABLE (not stored).
        if (!rateLimiter.tryAcquire(workspaceId)) {
            return unavailable(leadId, provider, "Rate limit reached — try again shortly.");
        }
        BigDecimal estimatedCost = BigDecimal.ZERO; // mock is free; real providers estimate here.
        if (!usageLedger.tryReserve(workspaceId, estimatedCost)) {
            return unavailable(leadId, provider, "Daily AI budget reached.");
        }

        CoachContext context = new CoachContext(
                leadId.toString(), lead.getName(), lead.getStage().name(), lead.getPurpose(),
                lead.getBudgetAed(), lead.getBedrooms(), lead.getEmirate(), lead.getCommunity(),
                lead.getPurchaseTimeframe(), lead.getStatedIntent(),
                requirement == null ? List.of() : requirement.getMustHaveAmenities(),
                ctxInteractions, matches, lang, PROMPT_VERSION);

        CoachGeneration generation;
        try {
            generation = callWithTimeoutAndRetries(provider, context);
        } catch (RuntimeException ex) {
            usageLedger.release(workspaceId, estimatedCost);
            log.warn("Coaching provider '{}' failed for lead {}: {}", provider.name(), leadId,
                    ex.getMessage());
            CoachingRun failed = persistFailure(workspaceId, leadId, lead.getVersion(), provider,
                    model, inputHash, ex.getMessage());
            return toResponse(failed, lead);
        }

        CoachResult result = CoachingValidator.validate(generation, scopedIds, matches, lang);
        if (choice.fallbackToMock() && choice.reason() != null) {
            result = withExtraWarning(result, choice.reason());
        }
        boolean anyStale = matches.stream().anyMatch(
                m -> m.tradeOffs().stream().anyMatch(t -> t.toLowerCase().contains("verify")));
        if (anyStale) {
            result = withExtraWarning(result,
                    "Some matched properties have unverified or stale facts — re-verify price and "
                            + "availability against an approved source before sharing.");
        }

        CoachingRun run = persistSuccess(workspaceId, leadId, lead.getVersion(), provider, model,
                inputHash, idempotencyKey, generation, result);
        usageLedger.release(workspaceId, estimatedCost);

        audit.record(workspaceId, authorId, "COACHING_RUN", "OK",
                Map.of("runId", run.getId().toString(), "leadId", leadId.toString(),
                        "provider", provider.name(), "live", provider.live()));

        return toResponse(run, lead);
    }

    public CoachingRunResponse get(UUID workspaceId, UUID runId) {
        CoachingRun run = runs.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Coaching run not found"));
        Lead lead = leads.findByIdAndWorkspaceId(run.getLeadId(), workspaceId).orElse(null);
        return toResponse(run, lead);
    }

    public List<CoachingRunResponse> history(UUID workspaceId, UUID leadId) {
        Lead lead = leads.findByIdAndWorkspaceId(leadId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
        return runs.findByLeadIdAndWorkspaceIdOrderByCreatedAtDesc(leadId, workspaceId).stream()
                .map(r -> toResponse(r, lead))
                .toList();
    }

    public void feedback(UUID workspaceId, UUID authorId, UUID runId, FeedbackRequest req) {
        CoachingRun run = runs.findByIdAndWorkspaceId(runId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Coaching run not found"));
        CoachingFeedback f = new CoachingFeedback();
        f.setRunId(run.getId());
        f.setAdvisorId(authorId);
        f.setHelpful(req.helpful());
        f.setEdited(Boolean.TRUE.equals(req.edited()));
        f.setActionTaken(req.actionTaken());
        f.setComment(req.comment());
        feedback.save(f);
        audit.record(workspaceId, authorId, "COACHING_FEEDBACK", "OK",
                Map.of("runId", run.getId().toString(), "helpful", String.valueOf(req.helpful())));
    }

    // --- internals ---

    private CoachGeneration callWithTimeoutAndRetries(CoachProvider provider, CoachContext context) {
        long timeout = props.getAi().getTimeoutMs();
        int attempts = 2;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Future<CoachGeneration> future = executor.submit(() -> provider.generate(context));
            try {
                return future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                future.cancel(true);
                last = new CoachProvider.CoachProviderException(
                        "Provider call failed (attempt " + attempt + "): " + rootMessage(e), e);
                if (attempt < attempts) {
                    sleep(150L * attempt);
                }
            }
        }
        throw last;
    }

    private CoachingRun persistSuccess(
            UUID workspaceId, UUID leadId, long leadVersion, CoachProvider provider, String model,
            String inputHash, String idempotencyKey, CoachGeneration gen, CoachResult result) {
        CoachingRun run = baseRun(workspaceId, leadId, leadVersion, provider, model, inputHash);
        run.setStatus(CoachingStatus.COMPLETED);
        run.setIdempotencyKey(blankToNull(idempotencyKey));
        run.setInputTokens(gen.inputTokens());
        run.setOutputTokens(gen.outputTokens());
        run.setEstimatedCostUsd(gen.estimatedCostUsd() == null ? BigDecimal.ZERO
                : gen.estimatedCostUsd());
        run.setResultJson(objectMapper.convertValue(result,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        return runs.save(run);
    }

    private CoachingRun persistFailure(
            UUID workspaceId, UUID leadId, long leadVersion, CoachProvider provider, String model,
            String inputHash, String message) {
        CoachingRun run = baseRun(workspaceId, leadId, leadVersion, provider, model, inputHash);
        run.setStatus(CoachingStatus.FAILED);
        // No idempotency key on failures, so the advisor can retry.
        run.setErrorMessage(truncate(message, 500));
        run.setEstimatedCostUsd(BigDecimal.ZERO);
        return runs.save(run);
    }

    private CoachingRun baseRun(UUID workspaceId, UUID leadId, long leadVersion,
            CoachProvider provider, String model, String inputHash) {
        CoachingRun run = new CoachingRun();
        run.setWorkspaceId(workspaceId);
        run.setLeadId(leadId);
        run.setInputHash(inputHash);
        run.setInputLeadVersion(leadVersion);
        run.setProvider(provider.name());
        run.setModel(model);
        run.setPromptVersion(PROMPT_VERSION);
        run.setLiveProvider(provider.live());
        return run;
    }

    private CoachingRunResponse unavailable(UUID leadId, CoachProvider provider, String message) {
        return new CoachingRunResponse(null, leadId.toString(), CoachingStatus.UNAVAILABLE.name(),
                provider.name(), provider.model(), PROMPT_VERSION, provider.live(), !provider.live(),
                false, 0, null, new Usage(null, null, BigDecimal.ZERO), null, message);
    }

    private CoachingRunResponse toResponse(CoachingRun run, Lead lead) {
        boolean outdated = lead != null && lead.getVersion() != run.getInputLeadVersion();
        CoachResult result = null;
        if (run.getStatus() == CoachingStatus.COMPLETED && run.getResultJson() != null) {
            result = objectMapper.convertValue(run.getResultJson(), CoachResult.class);
        }
        return new CoachingRunResponse(
                run.getId().toString(),
                run.getLeadId().toString(),
                run.getStatus().name(),
                run.getProvider(),
                run.getModel(),
                run.getPromptVersion(),
                run.isLiveProvider(),
                !run.isLiveProvider(),
                outdated,
                run.getInputLeadVersion(),
                run.getCreatedAt(),
                new Usage(run.getInputTokens(), run.getOutputTokens(), run.getEstimatedCostUsd()),
                result,
                run.getErrorMessage());
    }

    private CoachResult withExtraWarning(CoachResult r, String warning) {
        List<String> warnings = new ArrayList<>(r.warnings() == null ? List.of() : r.warnings());
        warnings.add(warning);
        return new CoachResult(r.summary(), r.facts(), r.hypotheses(), r.unknowns(), r.objections(),
                r.intentSignals(), r.nextAction(), r.alternativeApproach(), r.propertyMatches(),
                r.messageDraft(), r.language(), r.followUp(), warnings);
    }

    private String hash(UUID leadId, long version, Set<String> interactionIds,
            LeadRequirement requirement, String model) {
        StringBuilder sb = new StringBuilder();
        sb.append(leadId).append('|').append(version).append('|');
        interactionIds.stream().sorted().forEach(id -> sb.append(id).append(','));
        sb.append('|');
        if (requirement != null && requirement.getMustHaveAmenities() != null) {
            requirement.getMustHaveAmenities().stream().sorted()
                    .forEach(a -> sb.append(a.toLowerCase()).append(','));
        }
        sb.append('|').append(model).append('|').append(PROMPT_VERSION);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
