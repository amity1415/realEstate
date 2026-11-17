package ae.nest.crm.outbox;

import ae.nest.crm.domain.OutboxMessage;

/** Provider-neutral outbound delivery adapter. A real implementation connects an actual channel. */
public interface DeliveryProvider {

    String name();

    /** True for a real, connected channel; false for the simulated provider. */
    boolean live();

    DeliveryResult deliver(OutboxMessage message);

    /** Outcome of a delivery attempt. {@code simulated} marks a non-real (not actually sent) delivery. */
    record DeliveryResult(boolean success, boolean simulated, String providerRef, String error) {

        public static DeliveryResult ofSimulated() {
            return new DeliveryResult(true, true, "simulated", null);
        }

        public static DeliveryResult ofSent(String ref) {
            return new DeliveryResult(true, false, ref, null);
        }

        public static DeliveryResult ofFailure(String error) {
            return new DeliveryResult(false, false, null, error);
        }
    }
}
