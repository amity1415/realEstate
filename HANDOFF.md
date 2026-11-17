# NEST CRM — Milestone 1 handoff

## What was built

Milestones 1–2 of the brief, as a Java 21 / Spring Boot modular monolith with a same-origin web UI,
PostgreSQL persistence (Neon-ready), Flyway migrations and owner-only session auth:

- **Milestone 1** — durable CRM with no LLM dependency (leads, interactions, tasks, deterministic
  matching, import/export, metrics).
- **Milestone 2** — on-demand grounded AI coach behind a provider-neutral adapter, with a
  fully-working labelled mock provider, evidence validation, deterministic property constraints,
  editable drafts, stale labelling, usage/budget + rate limits, feedback and an evaluation suite.
- **Milestone 3** — authorised inventory import with provenance, a configurable freshness/verification
  workflow (UNVERIFIED until a human verifies; stale facts flagged in matches and coaching), a
  cohort-based conversion report, seed inventory and a pilot runbook.
- **Milestone 4** — integrations & scale: shared team roles + lead assignment, reliable in-app
  reminders (scheduled worker → once-only notifications), a durable outbox with a scheduled dispatcher
  (SKIP LOCKED claim, retries, dedupe) behind a provider-neutral delivery adapter (default = labelled
  simulated provider), and approved token-authenticated inbound lead capture. External messaging stays
  simulated until a real channel is connected.

**Done-when (Milestone 1):** an authenticated owner can manage a buyer from two browser sessions and
changes persist across restarts — satisfied (Spring Session JDBC + Flyway schema; state lives in
Postgres) and covered by `CrmApiIntegrationTest`.

**Done-when (Milestone 2):** the configured provider generates an evidence-backed next action with no
unsupported property claims and no automatic outreach, and mock mode is never presented as live.
Satisfied for the **mock** provider end-to-end (`CoachingIntegrationTest` + eval suite). The
**remaining step** is wiring the chosen real provider behind `CoachProvider` once the provider,
data-handling permissions and spend cap are decided (brief #4) — all grounding, validation, usage and
idempotency logic already applies to it unchanged.

**Done-when (Milestone 3):** the advisor can use the system in daily work with authorised, verified
inventory, outcomes are visible (snapshot + conversion reports), and production limitations/support
responsibilities are documented ([`PILOT.md`](PILOT.md)). The code path is complete and verified; the
**remaining real-world step** is loading the brokerage's actual authorised inventory (replacing the
synthetic seed) and running the pilot with one advisor's permitted leads to record real outcomes
(brief #5/#6).

## Implemented functions (live vs mock)

| Area | Status |
|---|---|
| Owner login, logout, session (JDBC-persisted), CSRF | **Live** |
| Lead CRUD, optimistic concurrency, archive/restore, stage history | **Live** |
| Interactions, tasks, requirements | **Live** |
| Deterministic property matching (hard constraints, sample exclusion) | **Live**, rule-based (no model) |
| JSON import (preview/commit, idempotent, dedup, rollback) | **Live** |
| CSV / JSON export (CSV injection-safe) | **Live** |
| Current-snapshot metrics | **Live** |
| Actuator health | **Live** |
| AI coach — adapter, grounding, validation, usage/budget, rate limit, idempotency, feedback, evals | **Live** via labelled **mock** provider |
| AI coach — real LLM provider | **Not wired** — one `CoachProvider` impl + credentials (brief #4) |
| Authorised inventory import (idempotent, provenance) | **Live** |
| Freshness/verification workflow + stale-flagged matches & coaching | **Live** |
| Cohort conversion report | **Live** |
| Inventory **fetching/scraping** | **Not built** — import from your authorised source, verify by hand |
| Team roles + lead assignment | **Live** |
| Reliable in-app reminders (scheduled) + notifications | **Live** |
| Durable outbox + scheduled dispatcher (retries, dedupe, SKIP LOCKED) | **Live** (delivery simulated) |
| Approved token-based inbound lead capture | **Live** |
| Real external messaging (WhatsApp/email send) | **Not connected** — simulated provider; wire a real channel + verify |

The coach never sends messages or changes lead state, property matches are always the deterministic
ones (samples excluded), evidence IDs are validated, and the mock is clearly labelled — never shown
as a live LLM.

## Verification done in this environment

- `./mvnw test` — unit tests (deterministic matcher, CSV writer) + an integration test that boots a
  **real embedded PostgreSQL**, runs the Flyway migrations, and exercises the HTTP API:
  unauthenticated rejection, login with a seeded owner, create/get, stale-version **409**, successful
  versioned update + stage-event history, **cross-workspace access denied**, deterministic matches
  (budget enforced, samples excluded), **idempotent import** (replay does not re-import), and CSV
  formula-injection neutralisation.
- Hibernate `ddl-auto: validate` runs against the Flyway schema at startup, so entity↔schema drift
  fails fast — the passing integration tests confirm they are aligned.
- Coaching: `CoachingIntegrationTest` (grounded run, evidence resolves, deterministic matches with
  samples excluded, idempotency + caching, outdated-after-change, feedback, cross-workspace denial),
  `CoachingValidatorTest`, `MockCoachProviderTest`, `UsageLedgerTest`, and a 10-case `CoachingEvalTest`
  eval suite.
- Inventory: `InventoryIntegrationTest` (idempotent import, UNVERIFIED → verify → VERIFIED_FRESH,
  needs-attention, stale-flagged matches, cohort conversion) and `FreshnessServiceTest`.
- Integrations: `IntegrationsIntegrationTest` (owner-vs-agent role enforcement → 403, assignment
  notification, due-task reminder delivered once (dedupe), outbox delivered via simulated provider +
  enqueue dedupe, inbound capture create + email-dedupe + invalid-token rejection).
- **53 tests total, all passing.**

## Not yet verified (needs real accounts / decisions)

- A real Railway deployment and a real Neon database (needs the account/project + connection details).
- Behaviour under a real pooled-vs-direct Neon connection split (configured via `MIGRATION_JDBC_URL`).
- Any AI provider (Milestone 2).

## Operational costs to verify before go-live

- Neon compute/storage for the chosen branch/region (owner already has a subscription — confirm it
  covers this usage; neither subscription is assumed to cover model inference).
- Railway service runtime.
- Model inference cost is **not** incurred in Milestone 1 (no provider calls).

## Deployment checklist (Railway + Neon)

- [ ] Confirm JDK 21 build; `docker build .` produces the runtime image.
- [ ] Re-check current Railway and Neon docs for any config/version changes.
- [ ] Build and test locally with synthetic data (`./mvnw test`, `docker compose up`).
- [ ] Create/select a Neon staging branch + least-privilege application role.
- [ ] Get **pooled** runtime and **direct** migration connection details; convert to `jdbc:postgresql://…`
      URLs (do not pass a bare `postgresql://` URL as JDBC) and require TLS (`sslmode=require` or as Neon
      recommends for pgJDBC).
- [ ] Keep `DB_POOL_MAX` small; tune from load.
- [ ] Set `MIGRATION_JDBC_URL` to the direct host so Flyway migrates over a controlled connection.
- [ ] Deploy the Docker image to Railway; it binds `PORT` and exposes `/actuator/health`; set the
      Railway health check to that path.
- [ ] Set all secrets as Railway variables (never in Git or the client bundle).
- [ ] Separate staging and production databases/roles; staging uses synthetic data.
- [ ] Bootstrap the owner once (`APP_OWNER_BOOTSTRAP=true` + email/password), then set it false and
      redeploy.
- [ ] Verify persistence after restart/redeploy and scoped access from a second browser session.
- [ ] Verify export + import rollback with staging data.
- [ ] Review with Amit, then promote to production under normal approval controls.

Health checks are not continuous uptime monitoring. Schema changes use backward-compatible
migrations; reverting a container image does not reverse a database migration.

## Decisions still open (brief §15)

1. Source repository / export location for the original demo (this build is a fresh, separate
   application repo; the original demo is preserved untouched).
2. Which Railway project and Neon database/region to use.
3. Owner login email + authentication method (local password is implemented; SSO would be additive).
4. LLM provider, permitted customer-data handling and spending cap (Milestone 2).
5. Authorised inventory source and freshness expectations (Milestone 3).
6. First pilot: Amit alone vs a brokerage team (roles are scaffolded for later).

**Defaults applied:** private single-owner pilot, synthetic data, manual notes, manually imported
inventory, no outbound messaging, no public registration, no paid add-ons.

## Next steps

**Finish Milestone 2 (real provider):** implement one `CoachProvider` for the chosen LLM (`live()` =
true), build the prompt from `CoachContext` (interaction text is untrusted; no tools), parse the
model's JSON into a `CoachGeneration`. Set `AI_ENABLED=true`, `AI_PROVIDER`, `AI_MODEL`, `AI_API_KEY`
and `AI_DAILY_BUDGET_USD`. Everything else — grounding validation, deterministic matches, usage
ledger, rate limiting, idempotency, caching, stale labelling, feedback — already applies unchanged.
Before uploading real customer conversations to any provider, confirm permitted provider use,
retention terms and brokerage permission (an old "permission to contact" is not that permission).

**Finish Milestone 3 (pilot):** load the brokerage's actual authorised inventory (replace the seed),
verify it, and run the pilot with one advisor's permitted leads — see [`PILOT.md`](PILOT.md). The
import, freshness/verification, stale-flagging and conversion reporting are all built and tested.

**Finish Milestone 4 (real messaging):** the internal mechanisms are built and tested. The remaining
step is wiring a real `DeliveryProvider` (WhatsApp/email) once the channel account, approved templates,
permission rules and duplicate-delivery checks are in place, then setting `DELIVERY_PROVIDER` to it.
The outbox (retries, dedupe, SKIP LOCKED claim) and scheduling already apply unchanged.

**Deployment (Neon + Railway):** see [`DEPLOY.md`](DEPLOY.md). Needs your Neon connection strings and a
re-authenticated Railway login (the CLI login here is expired), plus a GitHub remote for the push
(no `gh` available in this environment).

**Later:** document retrieval/embeddings only if inventory volume and evaluation results justify it.
