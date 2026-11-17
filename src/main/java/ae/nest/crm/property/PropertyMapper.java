package ae.nest.crm.property;

import ae.nest.crm.domain.Property;
import ae.nest.crm.property.PropertyDtos.PropertyResponse;
import java.util.List;

final class PropertyMapper {

    private PropertyMapper() {}

    static PropertyResponse toResponse(Property p) {
        return new PropertyResponse(
                p.getId().toString(),
                p.getExternalId(),
                p.getDeveloper(),
                p.getProject(),
                p.getUnitType(),
                p.getLocation(),
                p.getEmirate(),
                p.getCommunity(),
                p.getBedrooms(),
                p.getPriceAed(),
                p.getSizeSqft(),
                p.getAmenities() == null ? List.of() : p.getAmenities(),
                p.getCompletion(),
                p.getListingStatus(),
                p.isSample(),
                p.getUpdatedAt());
    }
}
