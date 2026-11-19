package ae.nest.crm.meta;

/** Fetches a lead's field data from Meta. Swappable so tests can stub the Graph API. */
public interface MetaLeadClient {

    /**
     * Resolve a {@code leadgen_id} to its field data. Returns null when the lead can't be fetched (e.g.
     * no page access token configured, or Graph error) — the caller then skips that lead.
     */
    MetaLead fetch(String leadgenId);
}
