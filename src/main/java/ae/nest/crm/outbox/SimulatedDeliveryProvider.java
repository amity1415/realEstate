package ae.nest.crm.outbox;

import ae.nest.crm.domain.OutboxMessage;
import org.springframework.stereotype.Component;

/**
 * The default delivery provider. It does NOT send anything — it simulates a successful delivery so the
 * durable outbox mechanism can be exercised end-to-end. Messages it handles are marked SIMULATED_SENT
 * and flagged {@code simulated}; this is never presented as real delivery.
 */
@Component
public class SimulatedDeliveryProvider implements DeliveryProvider {

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public DeliveryResult deliver(OutboxMessage message) {
        return DeliveryResult.ofSimulated();
    }
}
