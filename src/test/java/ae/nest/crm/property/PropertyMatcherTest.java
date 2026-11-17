package ae.nest.crm.property;

import static org.assertj.core.api.Assertions.assertThat;

import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.domain.Property;
import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the deterministic matcher — no Spring, no database. */
class PropertyMatcherTest {

    private Lead lead(BigDecimal budget, Integer bedrooms, String emirate, String community) {
        Lead l = new Lead();
        l.setBudgetAed(budget);
        l.setBedrooms(bedrooms);
        l.setEmirate(emirate);
        l.setCommunity(community);
        return l;
    }

    private Property property(String project, BigDecimal price, Integer bedrooms, String emirate,
            String community, List<String> amenities, boolean sample, String listingStatus) {
        Property p = new Property();
        p.setId(UUID.randomUUID());
        p.setProject(project);
        p.setPriceAed(price);
        p.setBedrooms(bedrooms);
        p.setEmirate(emirate);
        p.setCommunity(community);
        p.setAmenities(amenities);
        p.setSample(sample);
        p.setListingStatus(listingStatus);
        return p;
    }

    private LeadRequirement requirement(List<String> mustHave) {
        LeadRequirement r = new LeadRequirement();
        r.setMustHaveAmenities(mustHave);
        return r;
    }

    @Test
    void excludesOverBudget() {
        Lead l = lead(new BigDecimal("1000000"), null, null, null);
        Property under = property("A", new BigDecimal("900000"), 2, "Dubai", null, List.of(), false, "AVAILABLE");
        Property over = property("B", new BigDecimal("1200000"), 2, "Dubai", null, List.of(), false, "AVAILABLE");

        List<PropertyMatch> matches = PropertyMatcher.match(l, null, List.of(under, over), 3);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).property().project()).isEqualTo("A");
    }

    @Test
    void enforcesEmirateAndBedroomsExactly() {
        Lead l = lead(null, 2, "Dubai", null);
        Property wrongEmirate = property("A", new BigDecimal("1"), 2, "Sharjah", null, List.of(), false, "AVAILABLE");
        Property wrongBeds = property("B", new BigDecimal("1"), 3, "Dubai", null, List.of(), false, "AVAILABLE");
        Property ok = property("C", new BigDecimal("1"), 2, "dubai", null, List.of(), false, "AVAILABLE");

        List<PropertyMatch> matches =
                PropertyMatcher.match(l, null, List.of(wrongEmirate, wrongBeds, ok), 3);

        assertThat(matches).extracting(m -> m.property().project()).containsExactly("C");
    }

    @Test
    void requiresAllMustHaveAmenities() {
        Lead l = lead(null, null, null, null);
        LeadRequirement req = requirement(List.of("pool", "gym"));
        Property missing = property("A", new BigDecimal("1"), 1, null, null, List.of("Pool"), false, "AVAILABLE");
        Property has = property("B", new BigDecimal("1"), 1, null, null, List.of("Pool", "GYM", "Parking"), false, "AVAILABLE");

        List<PropertyMatch> matches = PropertyMatcher.match(l, req, List.of(missing, has), 3);

        assertThat(matches).extracting(m -> m.property().project()).containsExactly("B");
    }

    @Test
    void neverMatchesSamplesOrUnavailable() {
        Lead l = lead(null, null, null, null);
        Property sample = property("A", new BigDecimal("1"), 1, null, null, List.of(), true, "AVAILABLE");
        Property sold = property("B", new BigDecimal("1"), 1, null, null, List.of(), false, "SOLD");

        List<PropertyMatch> matches = PropertyMatcher.match(l, null, List.of(sample, sold), 3);

        assertThat(matches).isEmpty();
    }

    @Test
    void ordersCheaperHigherAndRespectsLimit() {
        Lead l = lead(new BigDecimal("2000000"), null, null, null);
        Property cheap = property("CHEAP", new BigDecimal("800000"), 1, null, null, List.of(), false, "AVAILABLE");
        Property mid = property("MID", new BigDecimal("1200000"), 1, null, null, List.of(), false, "AVAILABLE");
        Property dear = property("DEAR", new BigDecimal("1900000"), 1, null, null, List.of(), false, "AVAILABLE");

        List<PropertyMatch> matches = PropertyMatcher.match(l, null, List.of(dear, mid, cheap), 2);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).property().project()).isEqualTo("CHEAP");
        assertThat(matches.get(0).score()).isGreaterThanOrEqualTo(matches.get(1).score());
    }
}
