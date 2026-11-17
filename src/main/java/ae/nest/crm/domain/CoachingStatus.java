package ae.nest.crm.domain;

public enum CoachingStatus {
    /** A grounded result was produced and stored. */
    COMPLETED,
    /** The provider failed or timed out; no fabricated result is shown. */
    FAILED,
    /** Coaching was not run (budget cap, rate limit, or provider not configured). */
    UNAVAILABLE
}
