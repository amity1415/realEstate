package ae.nest.crm.coaching.provider;

/** Provider-neutral coaching adapter. Implementations must not perform any outward actions. */
public interface CoachProvider {

    /** Stable provider name (e.g. "mock", "anthropic"). Matched against {@code app.ai.provider}. */
    String name();

    /** The model identifier this provider will report for stored runs. */
    String model();

    /** True for a real LLM; false for a labelled mock. Surfaced to the advisor UI. */
    boolean live();

    /**
     * Produce a grounded coaching proposal from the scoped context. Must throw on failure/timeout
     * rather than fabricate a result.
     */
    CoachGeneration generate(CoachContext context) throws CoachProviderException;

    /** Thrown when a provider cannot produce a result (error, timeout, not configured). */
    class CoachProviderException extends RuntimeException {
        public CoachProviderException(String message) {
            super(message);
        }

        public CoachProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
