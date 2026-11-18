# NEST CRM — AI Design Document

**Where and how AI is (and can be) used in the NEST advisor CRM.**
Version 1.0 · for the NEST UAE real-estate advisor product.

This document maps the AI opportunities across the product, the architecture that supports them, and
the guardrails that keep them safe and honest. It is deliberately conservative: AI here **assists an
advisor**, it does not act on its own, and every claim it makes is grounded in the advisor's own data.

---

## 1. Principles (non-negotiable)

These hold for every AI feature below.

1. **Grounded, not generative-from-thin-air.** Facts come from the workspace's own data (leads,
   interactions, authorised inventory). Facts, inferences and unknowns are labelled separately, and
   every factual claim cites its evidence (an interaction ID, a property source/version).
2. **Human-in-the-loop.** AI produces drafts and suggestions; the advisor reviews, edits and acts.
   Nothing is sent, no stage is changed, no record is mutated by the model.
3. **No autonomous tools.** The model is never given tools that message a buyer, move a lead, or
   change inventory. Hard constraints (budget, emirate, bedrooms, must-haves) are enforced by
   **deterministic code**, never by the model.
4. **Untrusted inputs.** Buyer messages, brochures and uploaded documents are data, not instructions.
   Prompt-injection attempts inside them are ignored and flagged.
5. **Privacy & consent first.** Minimise and redact PII before it leaves the system. An old
   "permission to contact" is **not** permission to send customer conversations to an AI provider —
   that permission is separate and explicit.
6. **Cost- and failure-aware.** Spend caps, rate limits, caching, timeouts and bounded retries are
   mandatory. On provider failure the UI shows a safe "unavailable" state — never a fabricated result.
7. **Measurable.** Each feature ships with an evaluation set and tracks real outcomes; AI-assisted
   actions are recorded without attributing the sale itself to the AI.

---

## 2. Current state (what's already built)

**On-demand AI Sales Coach** (Milestone 2) — the reference implementation of the principles above.

- Provider-neutral adapter: `ae.nest.crm.coaching.provider.CoachProvider` with a clearly-labelled
  `MockCoachProvider` for development; a real LLM slots in behind the same interface.
- Scoped context assembly → model proposal → **validation layer** (`CoachingValidator`): evidence
  IDs must resolve to the lead's own interactions; property matches are replaced with the
  **deterministic** matches from `PropertyMatcher`; `BUYER_AGREED` timing without a time is downgraded.
- Controls: idempotency + input-hash caching, per-workspace rate limit, DB-backed spend ledger
  (`UsageLedger`), timeout + retries; stale runs are labelled **"Outdated — regenerate."**
- Output contract: confirmed facts (with evidence), hypotheses, unknowns, objections, intent signals,
  one next action + rationale + questions, an editable draft, suggested follow-up timing, warnings.
- Endpoints: `POST /leads/{id}/coaching-runs`, `GET /coaching-runs/{id}`, `POST /coaching-runs/{id}/feedback`.

Everything below reuses this architecture: the adapter, the validator pattern, the usage/rate
controls, and the deterministic-constraint boundary.

---

## 3. AI opportunity map

Ranked by value-to-effort. "Grounding" is the primary source of truth; "Guardrail" is the key risk
control; "Phase" is a suggested sequencing (1 = next, 3 = later / needs volume).

| # | Use case | Where in the UI | Grounding | Guardrail | Effort | Phase |
|---|---|---|---|---|---|---|
| 1 | **Sales coach** (built) | Lead drawer → "Coach this lead" | Interactions, inventory | Evidence validation, deterministic matches | — | ✅ |
| 2 | **Lead scoring / prioritisation** | Card badge + "Priority" sort | Stage history, engagement, requirement completeness | Transparent, rule+model hybrid; no protected attributes | M | 1 |
| 3 | **Message composer / reply drafting** | Outbox compose, coach draft | Lead context, approved templates | Draft only — never auto-send; per-channel tone | M | 1 |
| 4 | **Inbound lead parsing & enrichment** | Inbound capture, "paste a message" | Raw inbound text | Structured-output schema; dedupe by email; human confirm | M | 1 |
| 5 | **Natural-language search / ⌘K** | Command palette | Leads, inventory (SQL first) | NL → filters, not free SQL; scoped to workspace | M | 2 |
| 6 | **Semantic property matching / re-rank** | Lead → property matches | Authorised inventory + sources | Deterministic hard filters first, model only re-ranks | L | 2 |
| 7 | **Document Q&A (brochures, contracts)** | Property detail, a "Ask the brochure" box | Uploaded docs (RAG + citations) | Answers cite source+page; no answer if unsupported | L | 3 |
| 8 | **Win/loss & objection analytics** | Analytics dashboard | Stage events, coaching feedback, objections | Aggregate only; cohort denominators defined | M | 2 |
| 9 | **Pipeline forecasting / stale-deal alerts** | Dashboard + notifications | Stage timings, activity recency | Bands not false precision; "why" is explainable | L | 3 |
| 10 | **Meeting / viewing note summarisation** | Interaction entry | A raw note the advisor pastes | Summary cites the note; advisor edits before save | S | 2 |
| 11 | **Next-best-action nudges** | Lead card / tasks | Lead state + playbook rules | Suggests a task; advisor accepts; no auto-task | M | 2 |
| 12 | **Data-quality / anomaly detection** | Inventory "needs attention", leads | Field completeness, staleness, duplicates | Flags for review; never auto-edits | S | 2 |

### 3.1 Detail on the highest-value additions

**(2) Lead scoring / prioritisation.**
Surface a transparent priority score on each lead card and a "Priority" sort on the board. Inputs:
requirement completeness, recency and count of interactions, stage velocity (how fast it has moved),
budget realism vs inventory, response latency. Start with a **transparent rule-based score** (as the
brief already favours) and only add a learned model once there is labelled outcome data, and only on
behaviour signals. **Never** use nationality, religion, ethnicity, gender, health or inferred wealth —
these are explicitly excluded. Show the top 2–3 contributing factors next to the score so the advisor
sees *why*.

**(3) Message composer.**
Extend the existing coach draft into a first-class composer in the Outbox: pick a channel and tone
(formal / friendly / Arabic / English), the model drafts from the lead's real context and approved
templates. It stays a **draft in the outbox** — delivery is still the advisor's explicit action and
goes through the durable, dedupe-protected outbox. Never invent prices, discounts, deadlines, or
financing terms; those must come from verified property data.

**(4) Inbound parsing & enrichment.**
When a WhatsApp/email/portal enquiry arrives (or an advisor pastes one), the model extracts structured
fields (name, budget, bedrooms, emirate, intent) into the lead form for the advisor to confirm. This
makes the inbound-capture pipeline far more useful. Output is a **validated schema**; low-confidence
fields are left blank, not guessed; dedupe-by-email still applies.

**(5) Natural-language search in ⌘K.**
Let the command palette accept queries like *"2-bed Dubai buyers over 2M in viewing stage"*. The model
translates NL → a **constrained filter object** (stage, emirate, bedrooms, budget range), which runs
through the existing scoped SQL. The model never writes raw SQL and never sees other workspaces' data.

**(6) Semantic matching / re-rank.**
Keep the deterministic matcher as the **gatekeeper** (budget, emirate, bedrooms, must-haves, sample
exclusion, freshness). Add embeddings only to **re-rank** the already-eligible set by soft fit
(lifestyle cues in the buyer's stated intent vs property descriptions). Introduce embeddings "only
when measured retrieval failures justify them" — not before.

**(7) Document Q&A (RAG).**
For authorised brochures/price lists/contracts attached to a property, allow grounded Q&A with
**citations to source + page**. This is the one use case that genuinely needs a vector store. It must
refuse to answer when the documents don't support it, and must never present a model-named figure as
verified inventory.

---

## 4. Reference architecture

```
Advisor UI
  → Spring Boot API (workspace-scoped, auth enforced server-side)
      → Deterministic layer     (hard constraints, SQL retrieval, freshness) — source of truth
      → Context assembler       (minimised, redacted, bounded history)
      → Provider-neutral AI adapter  (CoachProvider / future: ScorerProvider, ParserProvider …)
          → LLM (or mock)       (structured JSON output, no tools)
      → Validation layer        (schema + evidence + deterministic-match substitution)
      → Usage ledger + cache    (spend cap, idempotency, input-hash cache)
      → Stored result + advisor feedback
  → Advisor reviews, edits, acts
```

**How new AI features plug in:**

- **New capability = new adapter interface**, mirroring `CoachProvider`: `name() / live() / generate()`
  with a typed context in and a typed proposal out. A labelled mock implements it first; the real
  provider is a single class behind the interface, selected by config (`AI_PROVIDER`, `AI_MODEL`,
  `AI_API_KEY`). The existing factory pattern (`CoachProviderFactory`) is the template.
- **Every proposal passes a validator** before storage/use, following `CoachingValidator`: resolve all
  referenced IDs against the scoped input, drop/flag unsupported content, and substitute deterministic
  results for anything the model must not decide.
- **Shared infra reused as-is:** `UsageLedger` (spend), rate limiter, idempotency keys, input-hash
  caching, timeouts + bounded retries, and the audit log (AI suggestions logged separately from
  advisor decisions).

**RAG (for document Q&A only), kept minimal:**
1. Ingest only **authorised** documents; record ownership, permission and version (reuse
   `property_sources`).
2. Chunk + embed; store vectors keyed by workspace + document version.
3. Retrieve top-k within the workspace; include **only** retrieved excerpts + necessary structured
   fields in the prompt.
4. Require citations (source + version + page); **refuse** when retrieval is insufficient.
5. Re-index on document change; mark stale chunks. A vector DB is introduced only here, only when
   inventory/document volume justifies it.

---

## 5. Data, privacy & UAE compliance

- **Minimise & redact** before any external LLM call: strip direct identifiers (full name, phone,
  email, document numbers) where they aren't needed for the task; pass stable internal IDs instead.
- **Explicit AI-processing consent** is separate from contact consent. Before real customer
  conversations are sent to a provider, confirm permitted provider use, data-retention terms and
  brokerage permission. The `contact_permissions` model tracks channel/purpose/status; add an explicit
  `AI_PROCESSING` purpose rather than overloading an existing one.
- **Provider retention:** prefer providers/configs with zero-retention or short retention for
  customer data; record the provider, model and prompt version on every run (already stored on
  `coaching_runs`).
- **Residency:** if data-residency is required, prefer a provider/region that keeps processing in an
  acceptable jurisdiction; otherwise keep AI on synthetic/sanitised data until cleared.
- **No protected attributes** in any scoring, routing or prioritisation feature.
- **Logging:** never log full prompts, secrets or lead PII by default; log safe metadata (run id,
  provider, token usage, cost) only.
- **Listing compliance:** Dubai listing permits and emirate-specific publication rules remain a
  brokerage responsibility; AI features that surface inventory must not bypass the freshness/permission
  checks.

---

## 6. Evaluation & rollout

- **Per-feature eval suite** (like the coach's): synthetic cases with expected behaviours, including
  adversarial ones (prompt injection in notes, missing evidence, no inventory match, stale price,
  conflicting notes). A feature does not ship until it passes.
- **Offline → shadow → assisted → on.** Start on mock/synthetic data; run shadow (compute but don't
  surface) to compare against advisor behaviour; then surface as optional; then default-on once metrics
  hold.
- **Metrics:** grounding accuracy (evidence resolves), constraint adherence (no hard-requirement
  violations), usefulness (advisor 👍/👎 and edit rate — already captured in `coaching_feedback`),
  safety (injection resisted, no fabricated figures), latency and cost per run.
- **Business reporting stays honest:** current-stage snapshot is reported separately from historical
  cohort conversion (as the Analytics dashboard already does); AI-assisted actions are recorded
  without attributing the sale to AI.

---

## 7. Phased roadmap

**Phase 1 — assistive, low-risk (next):**
- Wire a real provider behind the existing coach adapter (requires provider choice + spend cap +
  data-handling permission).
- Lead scoring/prioritisation (transparent rules first).
- Message composer in the Outbox (drafts only).
- Inbound parsing/enrichment (structured extraction, human-confirm).

**Phase 2 — intelligence on existing data:**
- NL search in ⌘K; next-best-action nudges; note summarisation; data-quality flags.
- Win/loss & objection analytics on the dashboard.
- Semantic **re-rank** of already-eligible property matches.

**Phase 3 — needs volume / new infra:**
- Document Q&A with citations (RAG + vector store).
- Pipeline forecasting / stale-deal prediction.

---

## 8. Non-goals (explicitly out of scope)

- AI that **sends** messages, **changes** stages, or **edits** records autonomously.
- AI that **invents** prices, discounts, payment plans, availability, yields, visa eligibility or
  financing approval.
- Scoring, routing or targeting that uses nationality, religion, ethnicity, gender, health or inferred
  wealth.
- Fake scarcity, fear-based pressure or personalised emotional manipulation.
- Shipping any provider integration as "live" before it is connected, permissioned and evaluated.

---

*This document is a living design; revisit it as provider choice, data-handling permissions and real
pilot outcomes are settled (see also `NEST_UAE_AI_Sales_Coach_Codex_Brief.md`, `HANDOFF.md`, `PILOT.md`).*
