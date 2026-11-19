package ae.nest.crm.meta;

import ae.nest.crm.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real Graph API client. Meta webhooks never carry PII — they give a {@code leadgen_id} that must be
 * exchanged, server-to-server with a page access token, for the submitted field data.
 */
@Component
public class GraphMetaLeadClient implements MetaLeadClient {

    private static final Logger log = LoggerFactory.getLogger(GraphMetaLeadClient.class);

    private final AppProperties props;
    private final RestClient http;

    public GraphMetaLeadClient(AppProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public MetaLead fetch(String leadgenId) {
        AppProperties.Meta cfg = props.getMeta();
        String token = cfg.getPageAccessToken();
        if (token == null || token.isBlank()) {
            log.warn("Meta page access token not configured; cannot fetch leadgen {}.", leadgenId);
            return null;
        }
        try {
            String url = "https://graph.facebook.com/" + cfg.getGraphVersion() + "/" + leadgenId
                    + "?fields=field_data,ad_id,form_id,campaign_name,created_time"
                    + "&access_token=" + token;
            JsonNode root = http.get().uri(url).retrieve().body(JsonNode.class);
            if (root == null) {
                return null;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            JsonNode fd = root.get("field_data");
            if (fd != null && fd.isArray()) {
                for (JsonNode f : fd) {
                    String name = f.path("name").asText(null);
                    JsonNode values = f.get("values");
                    String value = (values != null && values.isArray() && values.size() > 0)
                            ? values.get(0).asText(null) : null;
                    if (name != null && value != null) {
                        fields.put(name, value);
                    }
                }
            }
            return new MetaLead(
                    leadgenId,
                    fields,
                    text(root, "ad_id"),
                    text(root, "form_id"),
                    text(root, "campaign_name"),
                    text(root, "created_time"));
        } catch (RuntimeException e) {
            log.warn("Failed to fetch Meta leadgen {}: {}", leadgenId, e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
