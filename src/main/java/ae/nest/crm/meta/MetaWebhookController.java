package ae.nest.crm.meta;

import ae.nest.crm.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta (Facebook/Instagram) Lead Ads webhook.
 *
 * <ul>
 *   <li>GET performs the one-time subscription handshake (echoes {@code hub.challenge} when the verify
 *       token matches).
 *   <li>POST receives lead notifications; every delivery's HMAC signature is validated against the app
 *       secret before anything is processed. Unverified payloads are rejected and never touched.
 * </ul>
 *
 * The endpoint is a thin, public front door — authenticated by signature, not a session — that hands
 * verified payloads to {@link MetaLeadService}.
 */
@RestController
@RequestMapping("/webhooks/meta/leads")
public class MetaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookController.class);

    private final AppProperties props;
    private final MetaLeadService service;
    private final ObjectMapper objectMapper;

    public MetaWebhookController(
            AppProperties props, MetaLeadService service, ObjectMapper objectMapper) {
        this.props = props;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /** Subscription verification handshake. */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        AppProperties.Meta cfg = props.getMeta();
        if (!cfg.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Meta webhook not configured");
        }
        if ("subscribe".equals(mode) && cfg.getVerifyToken().equals(verifyToken) && challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    /** Signed lead delivery. Always answers 200 once verified, so Meta does not retry-storm. */
    @PostMapping
    public ResponseEntity<String> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {
        AppProperties.Meta cfg = props.getMeta();
        if (!cfg.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not configured");
        }
        if (body == null || !MetaSignature.valid(body, signature, cfg.getAppSecret())) {
            log.warn("Rejected a Meta webhook delivery with an invalid or missing signature.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        try {
            JsonNode payload = objectMapper.readTree(body);
            int n = service.ingest(payload);
            if (n > 0) {
                log.info("Meta webhook: ingested {} lead(s).", n);
            }
        } catch (Exception e) {
            // Payload was authentic but unprocessable — log and still ack so Meta stops retrying.
            log.warn("Meta webhook payload could not be processed: {}", e.getMessage());
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("EVENT_RECEIVED");
    }
}
