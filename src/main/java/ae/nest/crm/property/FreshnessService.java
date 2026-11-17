package ae.nest.crm.property;

import ae.nest.crm.config.AppProperties;
import ae.nest.crm.domain.PropertySource;
import ae.nest.crm.property.PropertyDtos.FreshnessInfo;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Evaluates property freshness against a configurable policy. A property is only VERIFIED_FRESH when
 * it has a VALID source verified within the freshness window; otherwise it is STALE or UNVERIFIED.
 * Imported fields are never treated as verified automatically.
 */
@Service
public class FreshnessService {

    public static final String VERIFIED_FRESH = "VERIFIED_FRESH";
    public static final String STALE = "STALE";
    public static final String UNVERIFIED = "UNVERIFIED";

    private final AppProperties props;

    public FreshnessService(AppProperties props) {
        this.props = props;
    }

    public int freshnessDays() {
        return props.getInventory().getFreshnessDays();
    }

    public String status(PropertySource source) {
        if (source == null) {
            return UNVERIFIED;
        }
        boolean valid = "VALID".equalsIgnoreCase(source.getValidityStatus())
                && source.getVerifiedAt() != null;
        if (!valid) {
            return UNVERIFIED;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(freshnessDays()));
        return source.getVerifiedAt().isBefore(cutoff) ? STALE : VERIFIED_FRESH;
    }

    public boolean isStaleOrUnverified(PropertySource source) {
        return !VERIFIED_FRESH.equals(status(source));
    }

    public FreshnessInfo toInfo(PropertySource source) {
        if (source == null) {
            return new FreshnessInfo(UNVERIFIED, null, null, null, null, null, null, freshnessDays());
        }
        return new FreshnessInfo(
                status(source),
                source.getVerifiedAt(),
                source.getFetchedAt(),
                source.getApprovedSource(),
                source.getSourceOwner(),
                source.getPermission(),
                source.getValidityStatus(),
                freshnessDays());
    }
}
