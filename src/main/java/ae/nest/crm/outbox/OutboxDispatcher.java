package ae.nest.crm.outbox;

import ae.nest.crm.config.AppProperties;
import ae.nest.crm.domain.OutboxMessage;
import ae.nest.crm.outbox.DeliveryProvider.DeliveryResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Orchestrates outbox delivery: claim due messages, deliver each via the provider, record results. */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxTx tx;
    private final List<DeliveryProvider> providers;
    private final AppProperties props;

    public OutboxDispatcher(OutboxTx tx, List<DeliveryProvider> providers, AppProperties props) {
        this.tx = tx;
        this.providers = providers;
        this.props = props;
    }

    public record DispatchSummary(int claimed, int delivered, int failed) {}

    public DispatchSummary dispatchDue(int limit) {
        List<OutboxMessage> claimed = tx.claimBatch(limit);
        if (claimed.isEmpty()) {
            return new DispatchSummary(0, 0, 0);
        }
        DeliveryProvider provider = provider();
        int delivered = 0;
        int failed = 0;
        for (OutboxMessage m : claimed) {
            DeliveryResult result;
            try {
                result = provider.deliver(m);
            } catch (RuntimeException e) {
                result = DeliveryResult.ofFailure(e.getMessage());
            }
            tx.markResult(m.getId(), result.success(), result.simulated(),
                    result.success() ? null
                            : (result.error() == null ? "delivery error" : result.error()));
            if (result.success()) {
                delivered++;
            } else {
                failed++;
            }
        }
        log.debug("Outbox dispatch via '{}': claimed={}, delivered={}, failed={}",
                provider.name(), claimed.size(), delivered, failed);
        return new DispatchSummary(claimed.size(), delivered, failed);
    }

    private DeliveryProvider provider() {
        String name = props.getDelivery().getProvider();
        return providers.stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> providers.stream()
                        .filter(p -> p.name().equals("simulated"))
                        .findFirst()
                        .orElse(providers.get(0)));
    }
}
