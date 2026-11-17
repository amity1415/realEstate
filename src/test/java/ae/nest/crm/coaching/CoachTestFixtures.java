package ae.nest.crm.coaching;

import ae.nest.crm.coaching.provider.CoachContext;
import ae.nest.crm.coaching.provider.CoachContext.ContextInteraction;
import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builders for coaching unit tests / the eval suite. */
final class CoachTestFixtures {

    private CoachTestFixtures() {}

    static ContextInteraction interaction(String text) {
        return new ContextInteraction(UUID.randomUUID().toString(), "NOTE", Instant.now(), text);
    }

    static CoachContext context(
            BigDecimal budget,
            Integer bedrooms,
            String emirate,
            List<ContextInteraction> interactions,
            List<PropertyMatch> matches) {
        return new CoachContext(
                UUID.randomUUID().toString(),
                "Aisha Rahman",
                "QUALIFIED",
                "primary residence",
                budget,
                bedrooms,
                emirate,
                null,
                null,
                null,
                new ArrayList<>(),
                interactions,
                matches,
                "en",
                CoachingService.PROMPT_VERSION);
    }
}
