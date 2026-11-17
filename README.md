# NEST UAE — Advisor CRM + AI Sales Coach (Milestones 1–4)

A private advisor CRM for a UAE real-estate advisor, built as a **Java 21 / Spring Boot modular
monolith** with a same-origin web UI, secure owner login, PostgreSQL persistence (Neon-ready),
Flyway migrations, import/export, a deterministic property matcher and an **on-demand, grounded AI
sales coach**.

- **Milestone 1** — durable CRM, no LLM dependency.
- **Milestone 2** — on-demand AI coach: a provider-neutral adapter with a clearly-labelled **mock**
  provider (fully working for development), evidence-validated output, deterministic property
  constraints, editable drafts, stale-result labelling, usage/budget + rate limits, feedback, and a
  repeatable evaluation suite. No automatic messaging; nothing is sent. A real LLM provider is a
  single wiring point behind the adapter, pending the provider/credentials decision.
- **Milestone 3** — real inventory + pilot: authorised inventory import with provenance (approved
  source, owner, permission), a configurable freshness/verification workflow (imported data is
  UNVERIFIED until a human verifies it; stale/unverified facts are flagged in matches and coaching), a
  cohort-based conversion report (distinct from the current snapshot), seed inventory and a pilot
  runbook ([`PILOT.md`](PILOT.md)).
- **Milestone 4** — integrations & scale: shared team roles + lead assignment, reliable in-app
  reminders (a scheduled worker turns due tasks into once-only notifications), a durable **outbox**
  with a scheduled dispatcher (claims with `FOR UPDATE SKIP LOCKED`, retries + dedupe) delivering via
  a provider-neutral adapter — the default is a clearly-labelled **simulated** provider that never
  claims real delivery — and approved **token-authenticated inbound lead capture** (create-only,
  rate-limited, dedup by email). External messaging stays simulated until a real channel is connected
  and verified. See [`DEPLOY.md`](DEPLOY.md) for Neon + Railway deployment.

> Scope honesty: this is a working implementation to run and verify locally/on staging. It does not
> claim a production backend exists. The AI coach runs on the labelled mock until a real provider is
> configured — mock output is never presented as a live LLM. No outbound messaging, no public
> registration, synthetic data only, no paid add-ons.

## What's implemented

- **Owner-only auth** — JSON login, BCrypt password hashing, HttpOnly session cookies persisted via
  Spring Session JDBC (survive restarts, shared across replicas), CSRF protection for cookie writes,
  JSON 401/403 (no login-form redirects). One-time owner bootstrap from env.
- **Leads** — create / list (filter by stage, emirate, sample, archived, text search, paginated) /
  get / update with **optimistic concurrency (version check)** / archive + restore.
- **Requirements, interactions, tasks** — per-lead, all workspace-scoped server-side.
- **Pipeline + stage history** — every stage transition recorded in `lead_stage_events` for later
  conversion analysis.
- **Deterministic property matching** — budget, emirate, bedrooms and must-have amenities enforced
  in code; samples and non-available listings never matched; transparent fit reasons and trade-offs.
  No model involved.
- **Import** — JSON backup → preview (validation, dedup by email) → commit (idempotent by
  `Idempotency-Key`, transactional rollback on failure). Your original browser data is only read.
- **Export** — CSV (with CSV formula-injection neutralisation) and full JSON backup.
- **Metrics** — current-stage snapshot counts, deliberately **not** conversion rates.
- **Web UI** — same-origin NEST-styled pipeline board, lead drawer, import/export, served from
  Spring Boot (`/`).
- **OpenAPI/Swagger** at `/swagger-ui.html`.

### AI sales coach (Milestone 2)

- **Provider-neutral adapter** (`CoachProvider`) with a labelled **mock** provider that works fully
  offline. A real LLM slots in behind the same interface; `AI_ENABLED=false` and missing credentials
  fall back to the mock with an explicit warning — the mock is never shown as live.
- **Grounded output** — confirmed facts, hypotheses, unknowns, objections, intent signals, one next
  action + rationale + questions, an alternative approach, an editable follow-up draft, suggested
  follow-up timing, and warnings. Facts/objections/intent signals carry evidence (interaction IDs).
- **Validation safety net** — every evidence ID must be a valid UUID in the scoped interaction set or
  it is dropped; **property matches are always the deterministic matches computed in code**, never
  whatever a model names; samples are excluded; `BUYER_AGREED` timing without a time is downgraded to
  a suggestion. The model is never given tools and never changes lead state.
- **Prompt-injection resistance** — interaction text is treated as untrusted data; instructions
  embedded in notes are flagged and ignored, not executed.
- **Idempotent + cached** — `Idempotency-Key` prevents duplicate charges on double-clicks; identical
  scoped input reuses the prior run; a changed lead marks older runs **Outdated — regenerate**.
- **Controls** — per-workspace rate limit, DB-backed daily spend ledger with in-flight reservation,
  bounded synchronous call with timeout + retries. Provider failure shows a safe "unavailable" state,
  never a fabricated result.
- **Feedback + evaluation** — advisor feedback is stored separately from the suggestion; a repeatable
  eval suite covers upfront-payment, price, timeframe, no-response, already-purchased, viewing,
  incomplete-requirements, no-inventory-match and malicious-instruction scenarios.

### Inventory, freshness & pilot (Milestone 3)

- **Authorised inventory import** — `POST /imports/properties/preview` + `/commit` (idempotent by
  `Idempotency-Key`, transactional), dedup by `externalId`, with a `source` block (approved source,
  owner, permission) recorded per property. Imported properties are **UNVERIFIED** until a human
  verifies them — imported fields are never treated as verified.
- **Configurable freshness policy** — `INVENTORY_FRESHNESS_DAYS` (agreed with the brokerage, not a
  universal default). A property is `VERIFIED_FRESH` only with a `VALID` source verified inside the
  window; otherwise `STALE` or `UNVERIFIED`.
- **Verification workflow** — `POST /properties/{id}/verify` records the approved source/owner/
  permission and marks it fresh; `GET /properties/{id}` shows freshness/provenance;
  `GET /properties/needs-attention` lists stale/unverified inventory.
- **Stale-aware matching + coaching** — matches re-read current inventory and annotate stale/
  unverified properties with a "re-verify before sharing" trade-off; coaching adds a matching warning.
- **Cohort conversion report** — `GET /metrics/conversion?from=&to=`: the cohort of leads that entered
  the pipeline in the window, how many reached each stage, and the rate with an explicit denominator —
  reported separately from the current-stage snapshot.
- **Pilot runbook** — [`PILOT.md`](PILOT.md) covers loading authorised inventory, verification, daily
  use, recording outcomes, production limitations and support responsibilities. Seed example inventory
  at [`seed/authorised-inventory.sample.json`](seed/authorised-inventory.sample.json).

## Tech

Java 21 · Spring Boot 3.3 (Web, Security, Data JPA, Validation, Actuator) · PostgreSQL · Flyway ·
Spring Session JDBC · springdoc-openapi · Maven Wrapper · Docker. Tests run against a **real
PostgreSQL** with no Docker required (Zonky embedded Postgres).

## Prerequisites

- **JDK 21** (`java -version` shows 21). The build targets release 21.
- A PostgreSQL database. For local development, Docker is the easiest (see below) but any Postgres 14+
  works — just point `SPRING_DATASOURCE_URL` at it.

## Quick start (local)

**1. Start PostgreSQL** (Docker):

```bash
docker compose up -d db
```

Or use any existing Postgres and create a database `nest_crm` (user `nest` / password `nest`), or set
`SPRING_DATASOURCE_URL/USERNAME/PASSWORD` to your own.

**2. Bootstrap the owner and run** (first run only, to create the single owner account):

```bash
APP_OWNER_BOOTSTRAP=true APP_OWNER_EMAIL=owner@nest.local APP_OWNER_PASSWORD='ChangeMe123!' ./mvnw spring-boot:run
```

On subsequent runs, drop the bootstrap variables:

```bash
./mvnw spring-boot:run
```

**3. Open the app:** http://localhost:8080 — sign in with the owner email/password above.

Flyway applies the schema automatically on startup.

## Running the tests

```bash
./mvnw test
```

Unit tests cover the deterministic matcher, CSV writer, coaching validator, mock provider, usage
ledger, freshness policy, and a coaching **evaluation suite**. Three integration tests start a real
embedded PostgreSQL, run the Flyway migrations and exercise the HTTP layer: `CrmApiIntegrationTest`
(auth, optimistic concurrency, cross-workspace isolation, deterministic matching, idempotent import,
CSV safety), `CoachingIntegrationTest` (grounded coaching, evidence resolution, deterministic matches,
idempotency + caching, outdated detection, feedback, cross-workspace denial) and
`InventoryIntegrationTest` (inventory import + idempotency, verification/freshness, stale-flagged
matches, cohort conversion). **48 tests, all passing.**

## API (v1)

Base path `/api/v1`. All list endpoints paginate (`page`, `size`; `size` capped at 200).

| Method & path | Purpose |
|---|---|
| `POST /auth/login` · `POST /auth/logout` · `GET /auth/me` · `GET /auth/csrf` | Session auth |
| `GET /leads` · `POST /leads` · `GET /leads/{id}` · `PATCH /leads/{id}` | Leads (PATCH is version-checked) |
| `POST /leads/{id}/archive` · `POST /leads/{id}/restore` · `GET /leads/{id}/stage-history` | Lifecycle |
| `POST/GET /leads/{id}/interactions` | Interactions |
| `POST /leads/{id}/tasks` · `PATCH /tasks/{id}` · `GET /tasks?status=&dueBefore=&leadId=` | Follow-up tasks |
| `GET /properties` · `POST /properties` · `GET /properties/{id}` | Inventory (+ freshness detail) |
| `POST /imports/properties/preview` · `POST /imports/properties/commit` | Authorised inventory import |
| `POST /properties/{id}/verify` · `GET /properties/needs-attention` | Verification / freshness |
| `GET /leads/{id}/property-matches` | Deterministic matches (stale-flagged) |
| `GET /metrics/conversion?from=&to=` | Cohort conversion report |
| `POST /imports/leads/preview` · `POST /imports/leads/commit` (needs `Idempotency-Key`) | Import |
| `GET /exports/leads?format=csv|json` | Export |
| `POST /leads/{id}/coaching-runs` (optional `Idempotency-Key`) · `GET /leads/{id}/coaching-runs` | AI coach (generate / history) |
| `GET /coaching-runs/{id}` · `POST /coaching-runs/{id}/feedback` | Coaching run + feedback |
| `GET /users` · `POST /users` · `PATCH /users/{id}` · `POST /users/me/password` | Team (create/role; OWNER/ADMIN) |
| `POST /leads/{id}/assign` · `GET /leads?mine=true&ownerId=` | Lead assignment / filter |
| `GET /notifications` · `GET /notifications/unread-count` · `POST /notifications/{id}/read` | In-app notifications |
| `POST /leads/{id}/outbox` · `GET /leads/{id}/outbox` · `POST /outbox/{id}/cancel` | Durable outbox |
| `POST /inbound-sources` · `GET /inbound-sources` · `POST /inbound/leads` (token) | Approved inbound capture |
| `GET /metrics` · `GET /actuator/health` | Reporting / health |

See `/swagger-ui.html` for the full contract and request shapes.

## Deployment (Railway + Neon)

See **`HANDOFF.md`** for the full checklist. Summary:

1. Create a Neon staging branch; get **pooled** (runtime) and **direct** (migration) pgJDBC URLs with
   `sslmode=require`, and a least-privilege application role.
2. Set the variables from `.env.example` as Railway variables/secrets. Secrets never go in Git.
3. Deploy the Docker image. The app binds Railway's `PORT` and exposes `/actuator/health` for the
   health check.
4. Bootstrap the owner once (`APP_OWNER_BOOTSTRAP=true` + email/password), then set it back to false.
5. Verify persistence across a redeploy and scoped access from a second browser session.

Migrations are backward-compatible and run via Flyway on the **direct** connection. Reverting a
container image does **not** reverse a database migration.

## What is NOT included yet (by design)

- **No real LLM provider wired.** The coach runs on the labelled mock. Wiring a real provider is a
  single `CoachProvider` implementation plus `AI_PROVIDER`/`AI_MODEL`/`AI_API_KEY` — pending the
  provider choice, permitted customer-data handling and spend cap (brief decision #4). Consult the
  provider's current official SDK/API docs before implementing.
- No website lead capture, WhatsApp/email integration, shared team roles, or scheduled reminders
  (Milestone 4).
- No inventory **fetching/scraping** — inventory is imported from an authorised source you provide and
  verified by a human; the system flags staleness but does not pull listings. Dubai listing permits and
  emirate-specific publication rules remain a brokerage responsibility.

### Enabling a real AI provider later

1. Implement `ae.nest.crm.coaching.provider.CoachProvider` (`live()` returns `true`), building the
   prompt from `CoachContext` and parsing the model's JSON into a `CoachGeneration`. Treat interaction
   text as untrusted data and give the model no tools.
2. Set `AI_ENABLED=true`, `AI_PROVIDER=<your-name>`, `AI_MODEL`, `AI_API_KEY`, and a spend cap
   `AI_DAILY_BUDGET_USD`. The factory selects it automatically; the existing validator, usage ledger,
   rate limiter, idempotency and caching apply unchanged.

## Repository layout

```
src/main/java/ae/nest/crm/
  config/        security, web, OpenAPI, typed app properties
  security/      principal, user-details, current-user, owner bootstrap
  common/        error handling, pagination, audit, CSV writer
  domain/        JPA entities + enums
  repo/          Spring Data repositories (workspace-scoped finders)
  auth/ lead/ interaction/ task/ property/ importexport/ metrics/   feature modules
  coaching/      AI coach: service, validator, usage/rate guards, DTOs
  coaching/provider/   provider SPI, mock provider, provider factory
  property/      matcher, inventory import, freshness/verification
src/main/resources/
  application.yml
  db/migration/  Flyway V1 (schema) + V2 (Session) + V3 (coaching) + V4 (inventory sources)
seed/            synthetic authorised-inventory example for pilot import
  static/        same-origin web UI (index.html, app.js, styles.css)
src/test/...     unit + integration tests
```
