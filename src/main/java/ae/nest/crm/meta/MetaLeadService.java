package ae.nest.crm.meta;

import ae.nest.crm.config.AppProperties;
import ae.nest.crm.inbound.InboundDtos.InboundLeadRequest;
import ae.nest.crm.inbound.InboundService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a verified Meta Lead Ads webhook delivery into leads. For each {@code leadgen} change it
 * resolves the field data (from the Graph API, or inline when a test harness includes it) and funnels
 * it through the existing token-authenticated inbound intake — so Meta leads get the same dedupe,
 * attribution capture, assignee notification and scoring as any other inbound lead.
 */
@Service
public class MetaLeadService {

    private static final Logger log = LoggerFactory.getLogger(MetaLeadService.class);
    private static final String CHANNEL = "Meta Lead Ads";

    private final AppProperties props;
    private final MetaLeadClient client;
    private final InboundService inbound;

    public MetaLeadService(AppProperties props, MetaLeadClient client, InboundService inbound) {
        this.props = props;
        this.client = client;
        this.inbound = inbound;
    }

    /** Processes every leadgen change in the payload; never throws, so Meta always gets its 200. */
    public int ingest(JsonNode payload) {
        String sourceToken = props.getMeta().getSourceToken();
        if (sourceToken == null || sourceToken.isBlank()) {
            log.warn("Meta source token not configured; received a webhook but cannot route the lead(s).");
            return 0;
        }
        JsonNode entries = payload == null ? null : payload.get("entry");
        if (entries == null || !entries.isArray()) {
            return 0;
        }
        int processed = 0;
        for (JsonNode entry : entries) {
            JsonNode changes = entry.get("changes");
            if (changes == null || !changes.isArray()) {
                continue;
            }
            for (JsonNode change : changes) {
                if (!"leadgen".equals(change.path("field").asText(""))) {
                    continue;
                }
                JsonNode value = change.get("value");
                if (value == null) {
                    continue;
                }
                try {
                    if (ingestOne(value, sourceToken)) {
                        processed++;
                    }
                } catch (RuntimeException e) {
                    log.warn("Failed to ingest a Meta lead: {}", e.getMessage());
                }
            }
        }
        return processed;
    }

    private boolean ingestOne(JsonNode value, String sourceToken) {
        String leadgenId = value.path("leadgen_id").asText(null);
        if (leadgenId == null) {
            return false;
        }
        // Prefer inline field data (test deliveries); otherwise exchange the id via the Graph API.
        MetaLead lead = value.has("field_data") ? fromInline(leadgenId, value) : client.fetch(leadgenId);
        if (lead == null) {
            log.warn("Could not resolve field data for Meta leadgen {}.", leadgenId);
            return false;
        }
        inbound.intake(sourceToken, toRequest(lead));
        return true;
    }

    private static MetaLead fromInline(String leadgenId, JsonNode value) {
        Map<String, String> fields = new LinkedHashMap<>();
        JsonNode fd = value.get("field_data");
        if (fd != null && fd.isArray()) {
            for (JsonNode f : fd) {
                String name = f.path("name").asText(null);
                JsonNode values = f.get("values");
                String v = (values != null && values.isArray() && values.size() > 0)
                        ? values.get(0).asText(null) : null;
                if (name != null && v != null) {
                    fields.put(name, v);
                }
            }
        }
        return new MetaLead(
                leadgenId, fields,
                text(value, "ad_id"), text(value, "form_id"),
                text(value, "campaign_name"), text(value, "created_time"));
    }

    private InboundLeadRequest toRequest(MetaLead lead) {
        String name = lead.field("full_name", "name");
        if (name == null) {
            String first = lead.field("first_name");
            String last = lead.field("last_name");
            name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            if (name.isBlank()) {
                name = null;
            }
        }
        String email = lead.field("email");
        String phone = lead.field("phone_number", "phone");
        String emirate = lead.field("city", "emirate", "location");

        StringBuilder msg = new StringBuilder("Lead Ads submission");
        if (lead.campaignName() != null) {
            msg.append(" — campaign: ").append(lead.campaignName());
        }
        for (String key : lead.customFieldNames()) {
            String v = lead.fields().get(key);
            if (v != null && !v.isBlank()) {
                msg.append("\n").append(key.replace('_', ' ')).append(": ").append(v);
            }
        }

        return new InboundLeadRequest(
                name, email, phone, msg.toString(),
                null, null, emirate,
                CHANNEL, "meta", "paid_social", lead.campaignName(),
                lead.adId(), lead.formId(), null);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
