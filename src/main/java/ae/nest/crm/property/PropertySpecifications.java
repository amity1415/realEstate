package ae.nest.crm.property;

import ae.nest.crm.domain.Property;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class PropertySpecifications {

    private PropertySpecifications() {}

    public static Specification<Property> inWorkspace(UUID workspaceId) {
        return (root, q, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
    }

    public static Specification<Property> emirate(String emirate) {
        if (!StringUtils.hasText(emirate)) {
            return null;
        }
        return (root, q, cb) -> cb.equal(cb.lower(root.get("emirate")), emirate.toLowerCase());
    }

    public static Specification<Property> bedrooms(Integer bedrooms) {
        if (bedrooms == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("bedrooms"), bedrooms);
    }

    public static Specification<Property> maxPrice(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("priceAed"), maxPrice);
    }

    public static Specification<Property> sampleIncluded(boolean includeSamples) {
        if (includeSamples) {
            return null;
        }
        return (root, q, cb) -> cb.isFalse(root.get("sample"));
    }
}
