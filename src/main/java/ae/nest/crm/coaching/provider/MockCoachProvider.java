package ae.nest.crm.coaching.provider;

import ae.nest.crm.coaching.CoachingDtos.Fact;
import ae.nest.crm.coaching.CoachingDtos.FollowUp;
import ae.nest.crm.coaching.CoachingDtos.Hypothesis;
import ae.nest.crm.coaching.CoachingDtos.IntentSignal;
import ae.nest.crm.coaching.CoachingDtos.NextAction;
import ae.nest.crm.coaching.CoachingDtos.Objection;
import ae.nest.crm.coaching.provider.CoachContext.ContextInteraction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline coach used for development and tests. It is clearly a mock and must never be
 * presented as a live LLM. It classifies interactions by keyword to surface objections, questions and
 * a grounded next action; it never invents prices, discounts, deadlines or financing, and it treats
 * all interaction text as data — instructions embedded in notes are ignored, not executed.
 */
@Component
public class MockCoachProvider implements CoachProvider {

    private static final Map<String, List<String>> SIGNALS = new LinkedHashMap<>();

    static {
        SIGNALS.put("UPFRONT_PAYMENT",
                List.of("down payment", "downpayment", "down-payment", "upfront", "up front",
                        "deposit", "initial payment"));
        SIGNALS.put("TOTAL_PRICE",
                List.of("too expensive", "expensive", "over budget", "price is high", "too much",
                        "cheaper", "lower price", "reduce the price"));
        SIGNALS.put("TIMING",
                List.of("wait", "later", "not now", "next year", "think about it", "no rush",
                        "hold off", "maybe i'll wait", "maybe i will wait"));
        SIGNALS.put("ALREADY_PURCHASED",
                List.of("already bought", "already purchased", "bought another", "purchased elsewhere",
                        "bought elsewhere"));
        SIGNALS.put("VIEWING_REQUEST",
                List.of("viewing", "view the", "see the", "visit", "site visit", "tour", "walk through",
                        "walkthrough"));
        SIGNALS.put("FINANCING",
                List.of("mortgage", "finance", "financing", "loan", "payment plan", "instalment",
                        "installment"));
    }

    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous", "ignore all previous", "disregard", "system prompt",
            "you are now", "new instructions", "act as");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock-model";
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public CoachGeneration generate(CoachContext ctx) {
        List<ContextInteraction> interactions = ctx.interactions() == null
                ? List.of() : ctx.interactions();

        // Classify: category -> interaction ids that triggered it.
        Map<String, Set<String>> hits = new LinkedHashMap<>();
        boolean injectionSeen = false;
        for (ContextInteraction i : interactions) {
            String text = i.text() == null ? "" : i.text().toLowerCase();
            for (var entry : SIGNALS.entrySet()) {
                for (String kw : entry.getValue()) {
                    if (text.contains(kw)) {
                        hits.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).add(i.id());
                        break;
                    }
                }
            }
            for (String marker : INJECTION_MARKERS) {
                if (text.contains(marker)) {
                    injectionSeen = true;
                }
            }
        }

        List<Fact> facts = new ArrayList<>();
        List<Hypothesis> hypotheses = new ArrayList<>();
        List<Objection> objections = new ArrayList<>();
        List<IntentSignal> intentSignals = new ArrayList<>();
        List<String> unknowns = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Objections + supporting facts/hypotheses, each citing the triggering interactions.
        if (hits.containsKey("UPFRONT_PAYMENT")) {
            List<String> ids = ids(hits, "UPFRONT_PAYMENT");
            objections.add(new Objection("UPFRONT_PAYMENT", true, ids));
            facts.add(new Fact("Buyer raised a concern about the upfront payment.", ids));
            hypotheses.add(new Hypothesis(
                    "An alternative payment schedule with a lower upfront amount may help.", "low", ids));
            unknowns.add("The upfront amount the buyer would be comfortable with");
            unknowns.add("Whether the total price is also a concern");
        }
        if (hits.containsKey("TOTAL_PRICE")) {
            List<String> ids = ids(hits, "TOTAL_PRICE");
            objections.add(new Objection("TOTAL_PRICE", true, ids));
            facts.add(new Fact("Buyer indicated the price is above what they expected.", ids));
            unknowns.add("The total budget the buyer is comfortable with");
        }
        if (hits.containsKey("TIMING")) {
            List<String> ids = ids(hits, "TIMING");
            objections.add(new Objection("TIMING", true, ids));
            hypotheses.add(new Hypothesis(
                    "The buyer may want to defer rather than decline.", "low", ids));
            unknowns.add("A suitable time to reconnect");
        }
        if (hits.containsKey("ALREADY_PURCHASED")) {
            List<String> ids = ids(hits, "ALREADY_PURCHASED");
            facts.add(new Fact("Buyer referred to having purchased a property.", ids));
            unknowns.add("Whether the buyer has completed a purchase or is still exploring");
        }
        if (hits.containsKey("VIEWING_REQUEST")) {
            List<String> ids = ids(hits, "VIEWING_REQUEST");
            intentSignals.add(new IntentSignal("Buyer asked to view a property.", ids));
            facts.add(new Fact("Buyer expressed interest in a viewing.", ids));
        }
        if (hits.containsKey("FINANCING")) {
            unknowns.add("Whether the buyer intends to pay in cash or use financing");
        }
        if (interactions.size() >= 3) {
            List<String> recent = interactions.stream().limit(3).map(ContextInteraction::id).toList();
            intentSignals.add(new IntentSignal("Sustained engagement across multiple interactions.",
                    recent));
        }

        // Missing structured requirements become explicit unknowns.
        if (ctx.budgetAed() == null) {
            unknowns.add("Budget");
        }
        if (ctx.bedrooms() == null) {
            unknowns.add("Preferred number of bedrooms");
        }
        if (isBlank(ctx.emirate())) {
            unknowns.add("Preferred emirate / location");
        }
        if (isBlank(ctx.purchaseTimeframe())) {
            unknowns.add("Purchase timeframe");
        }

        // Next action (decision tree over the detected state).
        NextAction nextAction = decideNextAction(ctx, hits, interactions.isEmpty());
        String alternative = alternative(hits);
        String draft = messageDraft(ctx, hits, interactions.isEmpty());

        // Warnings — property freshness, evidence sufficiency, and injection handling.
        if (ctx.eligibleMatches() == null || ctx.eligibleMatches().isEmpty()) {
            warnings.add("No eligible inventory currently matches the hard requirements.");
        }
        warnings.add("Payment plans, prices and financing are not asserted here — confirm them against "
                + "verified property sources before sharing figures.");
        if (interactions.isEmpty()) {
            warnings.add("No interaction history yet — the next step is discovery, not a strategy.");
        } else if (interactions.size() < 2) {
            warnings.add("Limited interaction history — treat inferences cautiously.");
        }
        if (injectionSeen) {
            warnings.add("A note contains text that looks like an instruction to the assistant; it was "
                    + "treated as buyer data only and not acted on.");
        }
        if (!isBlank(ctx.language()) && !"en".equalsIgnoreCase(ctx.language())) {
            warnings.add("Draft produced in English; the mock provider does not translate. Confirm the "
                    + "language before sending.");
        }

        String summary = summary(ctx, objections, interactions.isEmpty());
        FollowUp followUp = new FollowUp("SUGGESTED", null,
                "Agree a specific time with the buyer rather than assuming one.");

        int inputTokens = estimateTokens(ctx);
        int outputTokens = 220;

        return new CoachGeneration(summary, dedupeFacts(facts), hypotheses, dedupe(unknowns),
                objections, intentSignals, nextAction, alternative, draft, followUp,
                dedupe(warnings), inputTokens, outputTokens, BigDecimal.ZERO);
    }

    private NextAction decideNextAction(CoachContext ctx, Map<String, Set<String>> hits,
            boolean noHistory) {
        if (noHistory) {
            return new NextAction("CLARIFY_REQUIREMENTS",
                    "No interaction history yet; begin with discovery before recommending anything.",
                    List.of("What is the main purpose of the purchase?",
                            "What budget and location are you considering?",
                            "What is your timeframe?"));
        }
        if (hits.containsKey("ALREADY_PURCHASED")) {
            return new NextAction("RE_ENGAGE",
                    "The buyer referred to a purchase; confirm their status before further outreach.",
                    List.of("Have you completed a purchase, or are you still exploring options?"));
        }
        if (hits.containsKey("VIEWING_REQUEST")) {
            return new NextAction("SCHEDULE_VIEWING",
                    "The buyer asked to view a property; propose specific times.",
                    List.of("Which day and time would suit you for a viewing?"));
        }
        if (hits.containsKey("UPFRONT_PAYMENT") || hits.containsKey("TOTAL_PRICE")) {
            return new NextAction("CLARIFY_REQUIREMENTS",
                    "Confirm whether the concern is the upfront amount or the total price before "
                            + "changing the shortlist.",
                    List.of("Is the upfront amount the main concern, or is the overall budget too?",
                            "What initial amount would you be comfortable with?"));
        }
        if (hits.containsKey("TIMING")) {
            return new NextAction("FOLLOW_UP",
                    "The buyer wants to wait; agree a concrete check-in time.",
                    List.of("When would be a good time to reconnect?"));
        }
        if (ctx.eligibleMatches() != null && !ctx.eligibleMatches().isEmpty()) {
            return new NextAction("SHARE_SHORTLIST",
                    "Eligible options match the stated hard requirements; share a short, verified list.",
                    List.of());
        }
        return new NextAction("FOLLOW_UP",
                "Keep the conversation moving with a light, specific check-in.",
                List.of("Is there anything specific you'd like me to look into next?"));
    }

    private String alternative(Map<String, Set<String>> hits) {
        if (hits.containsKey("UPFRONT_PAYMENT") || hits.containsKey("TOTAL_PRICE")) {
            return "If the buyer declines to share figures, ask whether they would prefer options with a "
                    + "lower overall budget, and confirm flexibility before suggesting anything outside "
                    + "their stated hard requirements.";
        }
        if (hits.containsKey("TIMING")) {
            return "If the buyer is not ready, offer to send occasional relevant updates and agree a "
                    + "longer-dated check-in.";
        }
        return "If the buyer is unresponsive, try a brief, no-pressure message and a different channel "
                + "they have permitted.";
    }

    private String messageDraft(CoachContext ctx, Map<String, Set<String>> hits, boolean noHistory) {
        String name = isBlank(ctx.leadName()) ? "there" : ctx.leadName().split(" ")[0];
        if (noHistory) {
            return "Hi " + name + ", thanks for your interest. To find the right options, could you "
                    + "share your budget, preferred area and timeframe? I'll then put together a few "
                    + "suitable choices.";
        }
        if (hits.containsKey("UPFRONT_PAYMENT") || hits.containsKey("TOTAL_PRICE")) {
            return "Understood, " + name + " — thank you for sharing that. Would it help if I shortlisted "
                    + "options with a lower upfront amount? If you can let me know the initial amount "
                    + "you'd be comfortable with, I'll tailor the options accordingly.";
        }
        if (hits.containsKey("VIEWING_REQUEST")) {
            return "Happy to arrange a viewing, " + name + ". Which day and time work best for you? "
                    + "I'll confirm availability and send the details.";
        }
        if (hits.containsKey("TIMING")) {
            return "No problem, " + name + " — there's no rush. When would be a good time for me to "
                    + "check back in? I'll keep an eye out for suitable options in the meantime.";
        }
        return "Hi " + name + ", following up on our conversation. Is there anything specific you'd like "
                + "me to look into next? I'm happy to help whenever you're ready.";
    }

    private String summary(CoachContext ctx, List<Objection> objections, boolean noHistory) {
        if (noHistory) {
            return "New lead with no recorded interactions yet; the immediate step is discovery.";
        }
        if (!objections.isEmpty()) {
            String cats = objections.stream().map(Objection::category)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            return "Engaged buyer with stated concerns (" + cats + "); clarify before adjusting the "
                    + "shortlist.";
        }
        return "Active lead; keep the conversation moving toward a clear next step.";
    }

    private int estimateTokens(CoachContext ctx) {
        int chars = 200;
        if (ctx.interactions() != null) {
            for (ContextInteraction i : ctx.interactions()) {
                chars += i.text() == null ? 0 : i.text().length();
            }
        }
        return Math.max(50, chars / 4);
    }

    private static List<String> ids(Map<String, Set<String>> hits, String key) {
        return new ArrayList<>(hits.getOrDefault(key, Set.of()));
    }

    private static List<Fact> dedupeFacts(List<Fact> facts) {
        Map<String, Fact> seen = new LinkedHashMap<>();
        for (Fact f : facts) {
            seen.putIfAbsent(f.text(), f);
        }
        return new ArrayList<>(seen.values());
    }

    private static List<String> dedupe(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
