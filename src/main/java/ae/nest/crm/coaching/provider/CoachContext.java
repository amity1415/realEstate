package ae.nest.crm.coaching.provider;

import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Minimised, scoped input given to a coach provider. Interaction text and lead fields are untrusted
 * data: a provider implementation must treat any instructions inside them as content, never as
 * commands, and must never be given tools that send messages or change lead state.
 */
public record CoachContext(
        String leadId,
        String leadName,
        String stage,
        String purpose,
        BigDecimal budgetAed,
        Integer bedrooms,
        String emirate,
        String community,
        String purchaseTimeframe,
        String statedIntent,
        List<String> mustHaveAmenities,
        List<ContextInteraction> interactions,
        List<PropertyMatch> eligibleMatches,
        String language,
        String promptVersion) {

    /** One scoped interaction with its real ID, used for evidence citation and validation. */
    public record ContextInteraction(String id, String type, Instant occurredAt, String text) {}
}
