package ae.nest.crm.domain;

public enum OutboxStatus {
    /** Queued, waiting for its scheduled time. */
    PENDING,
    /** Claimed by a worker for a delivery attempt. */
    PROCESSING,
    /** Delivered by a SIMULATED provider — NOT actually sent to the recipient. */
    SIMULATED_SENT,
    /** Delivered by a real, connected provider. */
    SENT,
    /** Exhausted retries without delivery. */
    FAILED,
    /** Cancelled before delivery. */
    CANCELLED
}
