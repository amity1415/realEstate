package ae.nest.crm.inbound;

import ae.nest.crm.common.ApiExceptions.BadRequestException;
import ae.nest.crm.common.ApiExceptions.NotFoundException;
import ae.nest.crm.domain.InboundSource;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadAttribution;
import ae.nest.crm.domain.NotificationType;
import ae.nest.crm.inbound.InboundDtos.CreateSourceRequest;
import ae.nest.crm.inbound.InboundDtos.CreatedSourceResponse;
import ae.nest.crm.inbound.InboundDtos.InboundAck;
import ae.nest.crm.inbound.InboundDtos.InboundLeadRequest;
import ae.nest.crm.inbound.InboundDtos.SourceResponse;
import ae.nest.crm.interaction.InteractionDtos.InteractionCreateRequest;
import ae.nest.crm.interaction.InteractionService;
import ae.nest.crm.lead.LeadDtos.LeadCreateRequest;
import ae.nest.crm.lead.LeadService;
import ae.nest.crm.notification.NotificationService;
import ae.nest.crm.repo.InboundSourceRepository;
import ae.nest.crm.repo.LeadAttributionRepository;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.domain.InteractionType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approved inbound lead capture. Sources are managed by the owner/admin and authenticated by a
 * per-source token (only the hash is stored). The intake can only create a lead, is rate-limited, and
 * dedupes by email so a repeated website submission does not create duplicates.
 */
@Service
public class InboundService {

    private static final int MAX_PER_MINUTE = 60;

    private final InboundSourceRepository sources;
    private final LeadRepository leads;
    private final LeadService leadService;
    private final InteractionService interactions;
    private final NotificationService notifications;
    private final LeadAttributionRepository attribution;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, java.util.Deque<Instant>> rate = new ConcurrentHashMap<>();

    public InboundService(
            InboundSourceRepository sources,
            LeadRepository leads,
            LeadService leadService,
            InteractionService interactions,
            NotificationService notifications,
            LeadAttributionRepository attribution) {
        this.sources = sources;
        this.leads = leads;
        this.leadService = leadService;
        this.interactions = interactions;
        this.notifications = notifications;
        this.attribution = attribution;
    }

    @Transactional
    public CreatedSourceResponse createSource(
            UUID workspaceId, UUID createdBy, CreateSourceRequest req) {
        String token = "nest_" + base64Url(32);
        InboundSource s = new InboundSource();
        s.setWorkspaceId(workspaceId);
        s.setName(req.name().trim());
        s.setTokenHash(sha256(token));
        s.setCreatedBy(createdBy);
        s.setActive(true);
        if (req.defaultOwnerId() != null && !req.defaultOwnerId().isBlank()) {
            s.setDefaultOwnerId(UUID.fromString(req.defaultOwnerId()));
        } else {
            s.setDefaultOwnerId(createdBy);
        }
        s = sources.save(s);
        return new CreatedSourceResponse(toResponse(s), token);
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> list(UUID workspaceId) {
        return sources.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(InboundService::toResponse)
                .toList();
    }

    @Transactional
    public SourceResponse setActive(UUID workspaceId, UUID id, boolean active) {
        InboundSource s = sources.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Inbound source not found"));
        s.setActive(active);
        return toResponse(sources.save(s));
    }

    /** Token-authenticated intake. Creates (or dedupes to) a lead and notifies the assignee. */
    @Transactional
    public InboundAck intake(String token, InboundLeadRequest req) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Missing inbound token.");
        }
        InboundSource source = sources.findByTokenHashAndActiveTrue(sha256(token))
                .orElseThrow(() -> new BadRequestException("Invalid or inactive inbound token."));
        if (!allow(source.getId())) {
            throw new BadRequestException("Rate limit exceeded for this source.");
        }

        String name = firstNonBlank(req.name(), req.email(), "Website enquiry");
        if ((req.name() == null || req.name().isBlank())
                && (req.email() == null || req.email().isBlank())) {
            throw new BadRequestException("Provide at least a name or an email.");
        }
        UUID workspaceId = source.getWorkspaceId();
        UUID owner = source.getDefaultOwnerId() == null ? source.getCreatedBy()
                : source.getDefaultOwnerId();

        // Dedupe by email: attach the enquiry to the existing lead rather than duplicating.
        if (req.email() != null && !req.email().isBlank()) {
            Lead existing = leads.findFirstByWorkspaceIdAndEmailIgnoreCase(
                    workspaceId, req.email().trim().toLowerCase()).orElse(null);
            if (existing != null) {
                logEnquiry(workspaceId, owner, existing.getId(), req, source.getName());
                upsertAttribution(workspaceId, existing.getId(), req, source.getName());
                notify(workspaceId, owner, existing.getId(), source.getName(), true);
                return new InboundAck("accepted", existing.getId().toString(), true);
            }
        }

        LeadCreateRequest create = new LeadCreateRequest(
                name, req.email(), req.phone(), source.getName(), null, null, req.budgetAed(),
                req.bedrooms(), req.emirate(), null, null, req.message(), false, null);
        var lead = leadService.create(workspaceId, owner, create);
        UUID leadId = UUID.fromString(lead.id());
        logEnquiry(workspaceId, owner, leadId, req, source.getName());
        upsertAttribution(workspaceId, leadId, req, source.getName());
        notify(workspaceId, owner, leadId, source.getName(), false);
        return new InboundAck("accepted", lead.id(), false);
    }

    /** Record where the lead came from. First touch is kept; last touch advances on every enquiry. */
    private void upsertAttribution(
            UUID workspaceId, UUID leadId, InboundLeadRequest req, String sourceName) {
        Instant now = Instant.now();
        LeadAttribution a = attribution.findByLeadId(leadId).orElseGet(() -> {
            LeadAttribution fresh = new LeadAttribution();
            fresh.setLeadId(leadId);
            fresh.setWorkspaceId(workspaceId);
            fresh.setFirstTouchAt(now);
            return fresh;
        });
        a.setChannel(firstNonBlank(req.channel(), sourceName));
        if (req.utmSource() != null && !req.utmSource().isBlank()) {
            a.setUtmSource(req.utmSource());
        }
        if (req.utmMedium() != null && !req.utmMedium().isBlank()) {
            a.setUtmMedium(req.utmMedium());
        }
        if (req.utmCampaign() != null && !req.utmCampaign().isBlank()) {
            a.setUtmCampaign(req.utmCampaign());
        }
        if (req.adId() != null && !req.adId().isBlank()) {
            a.setAdId(req.adId());
        }
        if (req.formId() != null && !req.formId().isBlank()) {
            a.setFormId(req.formId());
        }
        if (req.landingUrl() != null && !req.landingUrl().isBlank()) {
            a.setLandingUrl(req.landingUrl());
        }
        a.setLastTouchAt(now);
        attribution.save(a);
    }

    private void logEnquiry(UUID workspaceId, UUID owner, UUID leadId, InboundLeadRequest req,
            String sourceName) {
        String text = "Inbound enquiry via " + sourceName
                + (req.message() == null || req.message().isBlank() ? "" : ": " + req.message());
        interactions.add(workspaceId, owner, leadId,
                new InteractionCreateRequest(InteractionType.MESSAGE, Instant.now(), text, "inbound"));
    }

    private void notify(UUID workspaceId, UUID owner, UUID leadId, String sourceName,
            boolean duplicate) {
        notifications.create(workspaceId, owner, NotificationType.INBOUND_LEAD,
                duplicate ? "New enquiry on an existing lead" : "New inbound lead",
                "Via " + sourceName, leadId, null, null);
    }

    private boolean allow(UUID sourceId) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(60);
        var window = rate.computeIfAbsent(sourceId, k -> new java.util.ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= MAX_PER_MINUTE) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    private static SourceResponse toResponse(InboundSource s) {
        return new SourceResponse(s.getId().toString(), s.getName(), s.isActive(),
                s.getDefaultOwnerId() == null ? null : s.getDefaultOwnerId().toString(),
                s.getCreatedAt());
    }

    private String base64Url(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
