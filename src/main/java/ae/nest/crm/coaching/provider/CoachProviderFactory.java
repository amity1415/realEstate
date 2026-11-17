package ae.nest.crm.coaching.provider;

import ae.nest.crm.config.AppProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Chooses the coach provider for a request. When AI is disabled, or a configured real provider is not
 * wired or is missing credentials, it falls back to the labelled mock and says so — the mock is never
 * silently presented as a live LLM.
 */
@Component
public class CoachProviderFactory {

    private final List<CoachProvider> providers;
    private final AppProperties props;
    private final MockCoachProvider mock;

    public CoachProviderFactory(
            List<CoachProvider> providers, AppProperties props, MockCoachProvider mock) {
        this.providers = providers;
        this.props = props;
        this.mock = mock;
    }

    /** The selected provider plus whether it fell back to the mock and why. */
    public record ProviderChoice(CoachProvider provider, boolean fallbackToMock, String reason) {}

    public ProviderChoice choose() {
        AppProperties.Ai ai = props.getAi();
        if (!ai.isEnabled()) {
            return new ProviderChoice(mock, true, "AI is disabled (AI_ENABLED=false); using the mock.");
        }
        String desired = ai.getProvider();
        if (!StringUtils.hasText(desired) || "mock".equalsIgnoreCase(desired)) {
            return new ProviderChoice(mock, false, null);
        }
        Optional<CoachProvider> match = providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(desired))
                .findFirst();
        if (match.isEmpty()) {
            return new ProviderChoice(mock, true,
                    "Provider '" + desired + "' is not wired in this build; using the mock.");
        }
        if (match.get().live() && !StringUtils.hasText(ai.getApiKey())) {
            return new ProviderChoice(mock, true,
                    "Provider '" + desired + "' has no API key configured; using the mock.");
        }
        return new ProviderChoice(match.get(), false, null);
    }
}
