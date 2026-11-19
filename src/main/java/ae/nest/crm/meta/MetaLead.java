package ae.nest.crm.meta;

import java.util.List;
import java.util.Map;

/**
 * A lead's field data as returned by the Graph API for a {@code leadgen_id}, plus its ad/form/campaign
 * context. {@code fields} maps a Meta question name (e.g. "email", "full_name", "phone_number") to the
 * submitted value.
 */
public record MetaLead(
        String leadgenId,
        Map<String, String> fields,
        String adId,
        String formId,
        String campaignName,
        String createdTime) {

    /** First present value among the given Meta field names (case-insensitive), else null. */
    public String field(String... names) {
        for (String n : names) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(n)
                        && e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue().trim();
                }
            }
        }
        return null;
    }

    /** Field names that are not standard contact details — kept for the enquiry note. */
    public List<String> customFieldNames() {
        return fields.keySet().stream()
                .filter(k -> !List.of("email", "phone_number", "phone", "full_name", "first_name",
                        "last_name", "name").contains(k.toLowerCase()))
                .toList();
    }
}
