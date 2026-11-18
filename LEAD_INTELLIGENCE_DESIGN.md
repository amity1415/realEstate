# Marketing Intake & Lead Intelligence — Design

**Goal:** (A) when social-media marketing creates interest, that lead lands in your NEST dashboard,
attributed to the campaign and ready to action; and (B) across a large lead DB (10k+), automatically
surface **who is a potential/hot client**, so you work the ~5% that matter instead of trawling 10k.

This builds directly on two use cases already mapped in `AI_DESIGN.md` — **#4 inbound capture** and
**#2 lead scoring** — and on infrastructure the product already has (token inbound, notifications,
scheduled workers, the deterministic matcher).

---

## Part A — Marketing intake pipeline

**Sources → how they connect**

| Source | Mechanism | Status |
|---|---|---|
| Landing pages / website forms | `POST /inbound/leads` with a per-source token | **Built** |
| Meta (Instagram/Facebook) **Lead Ads** | Meta webhook → a connector → inbound | Phase 1 (recommended first) |
| Google / TikTok lead forms | Webhook or Zapier/Make → inbound | Phase 1–2 |
| WhatsApp click-to-chat / Business API | BSP (e.g. Meta Cloud API) → inbound message | Phase 2 (needs a WhatsApp BSP) |
| Instagram / Messenger DMs | Meta Messenger API | Phase 3 |

**Architecture**
- **Reuse the existing inbound capture** (token-authenticated, create-only, rate-limited, dedupe by
  email). Add thin **per-channel connectors** that normalise each platform's payload into the inbound
  schema (name, contact, campaign, first message, consent).
- **Attribution**: capture `utm_source / utm_medium / utm_campaign`, ad id, form id, landing URL,
  first-touch & last-touch → a new `lead_attribution` record. Now you can see *"this lead came from the
  Dubai-Marina-2BR Instagram campaign"* and later measure which campaigns actually close.
- **Consent at intake**: store marketing + channel + (separately) AI-processing consent — UAE PDPL and
  the brief both require this; an ad opt-in is not blanket permission to send conversations to an LLM.
- **Engagement signals**, not just form fills: page/listing views, time-on-listing, repeat visits,
  message opens/replies → logged as typed interactions that feed the score (Part B).

**New "Inbox" view** — freshly arrived marketing leads with a **source/campaign badge**, the first
message, the computed score (Part B), and one-click **claim → assign → qualify**. A notification fires
on arrival (the notification system already exists).

**Flow:** ad interest → webhook → normalise → dedupe → create/attach lead (stage `NEW`,
`source = campaign`) → log engagement signals → notify → advisor sees it in the Inbox → qualifies.

---

## Part B — Lead intelligence: find the potential clients in 10k

**Core:** a **lead score 0–100**, a **"why"** (top contributing factors), and a **segment**
(Hot / Warm / Nurture / Cold), computed across the whole DB, **sortable and filterable**.

**Signals** (behavioural + fit — **never** protected attributes):
- **Engagement** — recency & frequency of interactions, listing views, email/WhatsApp opens & replies,
  time-on-site, repeat visits, event attendance.
- **Intent** — explicit budget/timeframe, "ready to buy", viewing requests, shortlist/compare activity.
- **Fit** — requirement completeness, budget realism vs available inventory, matchability to stock.
- **Velocity** — stage progression speed, response latency.
- **Source quality** — campaigns/sources that historically convert.
- **Decay** — scores fade as a lead goes quiet (a 6-months-silent lead is not hot).

**Approach — phased (don't start with ML):**
1. **Phase 1 — transparent rule-based score (ship first).** A weighted sum of the signals above, each
   worth points, surfacing the top 2–3 drivers. Deterministic, explainable, needs no training data —
   and it already ranks 10k leads and floats the hottest few hundred today.
2. **Phase 2 — learned propensity model (once outcomes exist).** When there's labelled history (which
   leads became WON vs LOST), train an explainable model (gradient-boosted trees / logistic regression)
   on the behavioural features to predict likelihood-to-convert; show feature contributions (SHAP);
   calibrate to a probability **band**, not false precision.
3. **Phase 3 — LLM-assisted enrichment (optional).** Summarise a messy history into a one-line *"why
   this lead matters,"* and extract soft intent from free-text/DMs — grounded, cited, human-reviewed.

**Guardrails (critical):**
- **No protected attributes** (nationality, religion, ethnicity, gender, health, inferred wealth).
- **Explainable** — every score shows its top drivers; advisor can see and trust why.
- **Advisory only** — it ranks and nudges; it never auto-acts.
- **Decay & re-score** — a scheduled job recomputes scores as signals change (reuse the worker pattern).
- **Measured** — track whether high-scored leads actually convert more; recalibrate.

**Where it shows in the UI:**
- A **"Hot leads" / Prioritise view** — the 10k ranked, filterable by segment / score / source, with
  the "why."
- A **score chip** on each lead card + a "Priority" sort on the board.
- The **Inbox** (Part A) uses the score to triage new marketing leads instantly.
- **Analytics** — "score vs conversion" and **campaign ROI** (which sources produce hot leads that close).

**Data-model additions:**
- `lead_attribution` — utm_source/medium/campaign, ad_id, form_id, landing_url, first_touch, last_touch.
- `lead_signals` (or typed interactions) — page_view, listing_view, email_open, dm, viewing_request… + weight.
- `lead_scores` — lead_id, score, segment, top_factors (jsonb), model_version, computed_at (recomputed by a job).

---

## My recommendation

1. **Start with Part A inbound connectors + a transparent rule-based score (Phase 1).** It sits on the
   existing inbound + notification + scheduler infra, needs no ML, and immediately answers *"who among
   10k is worth my time."* This is the fastest path to value.
2. **Highest-leverage single integration: Meta Lead Ads** (most UAE property social spend is IG/FB) →
   a webhook into the existing inbound endpoint.
3. **Don't jump to ML first** — you need labelled WON/LOST history to train, and the rule-based score is
   explainable and effective now. Add the learned model once the pilot accumulates outcomes.
4. **Attribution is the quiet win** — capturing UTM/campaign from day one lets you spend marketing
   budget where it actually closes.
5. **Compliance** — capture marketing + AI-processing consent at intake (UAE PDPL); never ingest DMs or
   conversations into an LLM without explicit permission.

## What I'd build first (concrete, in order)

1. `lead_attribution` fields + capture in the inbound endpoint (UTM, campaign, ad/form id).
2. A **Meta Lead Ads webhook** connector → the existing inbound capture.
3. An **"Inbox" view** for new marketing leads (source/campaign badge, first message, claim/assign).
4. A transparent **`LeadScorer`** + `lead_scores` + a scheduled re-score + a score chip + a **"Hot
   leads"** ranked view.
5. (Later) learned propensity model + LLM "why this lead matters" enrichment.

Phases 1–4 are buildable now on the current stack; Phase 5 follows once there's outcome data and the
provider/consent questions are settled.
