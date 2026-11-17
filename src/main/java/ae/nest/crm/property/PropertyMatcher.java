package ae.nest.crm.property;

import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.domain.Property;
import ae.nest.crm.property.PropertyDtos.PropertyMatch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic property matcher. Hard requirements (budget, emirate, bedrooms, must-have amenities)
 * are enforced in code — a property is never returned unless it satisfies all specified hard
 * constraints. Sample inventory and non-available listings are never matched. No model is involved.
 */
public final class PropertyMatcher {

    private PropertyMatcher() {}

    /** Returns up to {@code limit} matches, strongest first, enforcing all hard constraints. */
    public static List<PropertyMatch> match(
            Lead lead, LeadRequirement requirement, List<Property> candidates, int limit) {

        List<String> mustHave = requirement == null || requirement.getMustHaveAmenities() == null
                ? List.of()
                : requirement.getMustHaveAmenities().stream()
                        .filter(a -> a != null && !a.isBlank())
                        .map(a -> a.toLowerCase().trim())
                        .toList();

        List<PropertyMatch> matches = new ArrayList<>();
        for (Property p : candidates) {
            // Never match samples or non-available listings.
            if (p.isSample()) {
                continue;
            }
            if (p.getListingStatus() != null
                    && !p.getListingStatus().equalsIgnoreCase("AVAILABLE")) {
                continue;
            }
            if (!satisfiesHardConstraints(lead, mustHave, p)) {
                continue;
            }
            matches.add(score(lead, p, mustHave));
        }

        return matches.stream()
                .sorted(Comparator
                        .comparingInt(PropertyMatch::score).reversed()
                        .thenComparing(m -> nullSafePrice(m.property().priceAed()))
                        .thenComparing(m -> m.property().id()))
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }

    private static boolean satisfiesHardConstraints(Lead lead, List<String> mustHave, Property p) {
        // Budget: must have a known price within budget when a budget is specified.
        if (lead.getBudgetAed() != null) {
            if (p.getPriceAed() == null || p.getPriceAed().compareTo(lead.getBudgetAed()) > 0) {
                return false;
            }
        }
        // Emirate: exact (case-insensitive) when specified.
        if (hasText(lead.getEmirate())) {
            if (!hasText(p.getEmirate()) || !p.getEmirate().equalsIgnoreCase(lead.getEmirate())) {
                return false;
            }
        }
        // Bedrooms: exact when specified.
        if (lead.getBedrooms() != null) {
            if (p.getBedrooms() == null || !p.getBedrooms().equals(lead.getBedrooms())) {
                return false;
            }
        }
        // Must-have amenities: property must include every one.
        if (!mustHave.isEmpty()) {
            Set<String> have = (p.getAmenities() == null ? List.<String>of() : p.getAmenities())
                    .stream()
                    .filter(a -> a != null)
                    .map(a -> a.toLowerCase().trim())
                    .collect(Collectors.toSet());
            if (!have.containsAll(mustHave)) {
                return false;
            }
        }
        return true;
    }

    private static PropertyMatch score(Lead lead, Property p, List<String> mustHave) {
        int score = 50;
        List<String> fit = new ArrayList<>();
        List<String> tradeOffs = new ArrayList<>();

        if (lead.getBudgetAed() != null && p.getPriceAed() != null
                && lead.getBudgetAed().signum() > 0) {
            BigDecimal ratio = p.getPriceAed()
                    .divide(lead.getBudgetAed(), 4, RoundingMode.HALF_UP);
            double r = ratio.doubleValue();
            score += (int) Math.round((1.0 - Math.min(r, 1.0)) * 30);
            fit.add("Within budget (AED " + strip(p.getPriceAed()) + " of AED "
                    + strip(lead.getBudgetAed()) + ")");
            if (r > 0.95) {
                tradeOffs.add("Priced near the top of the stated budget");
            }
        } else if (p.getPriceAed() != null) {
            fit.add("Priced at AED " + strip(p.getPriceAed()));
        }

        if (lead.getBedrooms() != null) {
            fit.add(bedroomsLabel(p.getBedrooms()) + " matches requirement");
        }
        if (hasText(lead.getEmirate())) {
            fit.add("Located in " + p.getEmirate());
        }
        if (!mustHave.isEmpty()) {
            fit.add("Includes all must-have amenities");
        }

        if (hasText(lead.getCommunity())) {
            if (hasText(p.getCommunity()) && p.getCommunity().equalsIgnoreCase(lead.getCommunity())) {
                score += 10;
                fit.add("In preferred community " + p.getCommunity());
            } else {
                tradeOffs.add("Community differs from the buyer's stated preference"
                        + (hasText(lead.getCommunity()) ? " (" + lead.getCommunity() + ")" : ""));
            }
        }

        int extraAmenities = p.getAmenities() == null ? 0 : p.getAmenities().size();
        score += Math.min(extraAmenities * 2, 10);

        if (hasText(p.getCompletion())
                && !p.getCompletion().toLowerCase().contains("ready")) {
            tradeOffs.add("Completion status: " + p.getCompletion()
                    + " — confirm handover timeline");
        }

        return new PropertyMatch(PropertyMapper.toResponse(p), score, fit, tradeOffs);
    }

    private static BigDecimal nullSafePrice(BigDecimal p) {
        return p == null ? new BigDecimal("999999999999") : p;
    }

    private static String bedroomsLabel(Integer bedrooms) {
        if (bedrooms == null) {
            return "Bedrooms";
        }
        return bedrooms == 0 ? "Studio" : bedrooms + "-bedroom";
    }

    private static String strip(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
