# NEST CRM — Single-advisor pilot runbook (Milestone 3)

This describes how to run the pilot with **one advisor and their permitted leads**, how inventory
freshness works, what the system does and does not do, and how outcomes are recorded. It is written
to be honest about limitations rather than to overclaim.

## 1. Before the pilot

- [ ] Confirm the advisor is the single owner for the pilot workspace (no shared team yet).
- [ ] Agree the **inventory freshness window** with the brokerage and set `INVENTORY_FRESHNESS_DAYS`
      (default 30). There is no assumed universal update frequency.
- [ ] Decide whether the AI coach runs on the **mock** (default) or a real provider. A real provider
      also requires: the provider choice, confirmed permitted use of customer data, retention terms,
      brokerage permission, and a spend cap (`AI_DAILY_BUDGET_USD`). An old "permission to contact" is
      **not** permission to upload conversations to an AI provider.
- [ ] Confirm the inventory you import is **authorised** for use (ownership + permission recorded on
      import).

## 2. Load authorised inventory

Inventory is imported with provenance and is **UNVERIFIED** until a human verifies it — imported
fields are never treated as verified automatically.

1. Prepare an inventory file in the shape of [`seed/authorised-inventory.sample.json`](seed/authorised-inventory.sample.json)
   (an object with a `source` block and a `properties` array; replace the synthetic example with the
   brokerage's actual authorised listings). Dedup is by `externalId`.
2. Preview, then commit (commit needs an `Idempotency-Key` and is transactional + idempotent):
   - `POST /api/v1/imports/properties/preview`
   - `POST /api/v1/imports/properties/commit` (header `Idempotency-Key: <unique>`)
   Or use the **Inventory** screen in the app (Preview → Commit).
3. **Verify** each property against its approved source (`POST /api/v1/properties/{id}/verify`, or the
   "Verify" button). Only verified properties within the freshness window are **VERIFIED_FRESH**;
   others are **STALE** or **UNVERIFIED** and are flagged in matches and coaching.

Samples remain clearly distinct (`sample: true`) and are never used in real customer shortlists,
drafts or coaching.

## 3. Daily use

- Manage the advisor's permitted leads on the pipeline board; log interactions and next actions.
- Use **Coach this lead** for an evidence-backed next step, objections, questions, an editable draft
  (never sent automatically) and up to three deterministic matches.
- Matches and coaching **flag stale/unverified inventory** — re-verify price and availability against
  the approved source before sharing figures with a customer.
- Re-check `GET /api/v1/properties/needs-attention` (or the Inventory screen's "Needs attention"
  filter) regularly and re-verify as inventory changes.

## 4. Recording outcomes

Outcomes come from data the system already captures — no manual spreadsheet:

- **Stage changes** are timestamped in `lead_stage_events` (with the reason you enter).
- **Current snapshot:** `GET /api/v1/metrics` — active leads by stage now (not a conversion rate).
- **Historical conversion:** `GET /api/v1/metrics/conversion?from=&to=` — cohort of leads that entered
  the pipeline in the window, how many reached each stage, and the rate with an explicit denominator.
  Current-stage counts and historical conversion are reported separately on purpose.
- **Coaching feedback:** 👍/👎 and comments are stored in `coaching_feedback`, separate from the AI
  suggestion, to improve objections/questions/matches over time.

When attributing results, record that an action was AI-assisted without attributing the sale itself to
the AI.

## 5. Production limitations (be explicit with the advisor)

- The AI coach runs on a **labelled mock** unless a real provider is configured; mock output is never
  presented as a live LLM.
- **No outbound messaging.** Drafts are created for the advisor to send manually; nothing is sent, and
  a drafted message is not a delivered message.
- **No automatic lead capture or inbound sync** (website/WhatsApp/email) and **no scheduled reminders**
  yet (Milestone 4). A due task shown on screen is not a delivered reminder.
- **Inventory is only as fresh as it is verified.** The system flags staleness; it does not fetch or
  scrape listings. Dubai listing permits and any emirate-specific publication requirements remain the
  brokerage's responsibility to verify before any public launch.
- **Single owner only.** Shared team roles/assignment are later (Milestone 4).
- Deployment health checks are not continuous uptime monitoring; set up monitoring separately.

## 6. Support responsibilities

- **Brokerage/advisor:** keep authorised inventory current and verified; confirm data-handling
  permission before any real customer text goes to an AI provider; own listing-permit compliance;
  review coaching drafts before sending.
- **Operator (whoever runs the deployment):** manage Railway/Neon, secrets and backups; apply
  schema migrations (backward-compatible; reverting a container image does not reverse a migration);
  watch the spend cap and usage ledger if a real provider is enabled; restore from backup if needed.
- **Escalation:** on provider failure the coach shows a safe "unavailable" state and never a fabricated
  result; retry later or fall back to manual workflow.

## 7. Exit criteria (Milestone 3 done-when)

The advisor can use the system in daily work with authorised, verified inventory; outcomes are visible
through the snapshot and conversion reports; and the limitations and support responsibilities above are
documented and understood.
