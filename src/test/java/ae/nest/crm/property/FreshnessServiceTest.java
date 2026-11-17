package ae.nest.crm.property;

import static org.assertj.core.api.Assertions.assertThat;

import ae.nest.crm.config.AppProperties;
import ae.nest.crm.domain.PropertySource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class FreshnessServiceTest {

    private FreshnessService service() {
        AppProperties props = new AppProperties();
        props.getInventory().setFreshnessDays(30);
        return new FreshnessService(props);
    }

    private PropertySource source(String validity, Instant verifiedAt) {
        PropertySource s = new PropertySource();
        s.setValidityStatus(validity);
        s.setVerifiedAt(verifiedAt);
        return s;
    }

    @Test
    void noSourceIsUnverified() {
        assertThat(service().status(null)).isEqualTo(FreshnessService.UNVERIFIED);
    }

    @Test
    void validAndRecentIsFresh() {
        assertThat(service().status(source("VALID", Instant.now())))
                .isEqualTo(FreshnessService.VERIFIED_FRESH);
    }

    @Test
    void validButOldIsStale() {
        Instant old = Instant.now().minus(40, ChronoUnit.DAYS);
        assertThat(service().status(source("VALID", old))).isEqualTo(FreshnessService.STALE);
    }

    @Test
    void unverifiedValidityIsUnverified() {
        assertThat(service().status(source("UNVERIFIED", Instant.now())))
                .isEqualTo(FreshnessService.UNVERIFIED);
    }
}
