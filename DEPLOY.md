# Deploying NEST CRM to Neon + Railway

The app is a Docker-built Spring Boot service. It binds Railway's `PORT`, exposes `/actuator/health`
for the health check, and runs Flyway migrations on startup. Secrets live only in Railway variables.

> What I (Claude) cannot do for you here, and why: your Railway CLI login is expired
> (`railway whoami` → "Unauthorized") and no Neon connection strings are available in this
> environment. Deployment needs both. The steps below are exact; where a step needs you, it says so.
> If you re-auth Railway and paste the Neon URLs, I can run the CLI steps for you.

## 0. Prerequisites you provide

- A **Neon** project (you have Pro). You'll create a database/branch and copy two connection strings.
- A **Railway** project (you have Pro). Either connect this GitHub repo, or deploy from the local CLI.
- This repo pushed to GitHub (see "Git push" at the bottom) if you use Railway's GitHub deploy.

## 1. Neon — database + connection strings

1. In the Neon console, create (or pick) a project and a **branch** for this environment (e.g.
   `staging`). Create a database `nest_crm` and a least-privilege application role.
2. Copy two connection strings from **Connect**:
   - **Pooled** (has `-pooler` in the host) → runtime.
   - **Direct** (no `-pooler`) → migrations.
3. Convert each to a **pgJDBC URL** (do not pass a bare `postgresql://` URL as JDBC):
   - `postgresql://USER:PASS@HOST/nest_crm?sslmode=require`
     → `jdbc:postgresql://HOST/nest_crm?sslmode=require`
   - Put the username/password in the `*_USERNAME` / `*_PASSWORD` variables, not in the URL.
   - Keep `sslmode=require` (or the parameters Neon recommends for pgJDBC).

## 2. Railway — service + variables

**Option A — deploy from GitHub (recommended):**
1. Push this repo to GitHub, then in Railway: **New Project → Deploy from GitHub repo** → pick it.
   Railway detects the `Dockerfile` and builds it.

**Option B — deploy from the local CLI:**
```bash
railway login
railway init            # or: railway link   (to an existing project)
railway up              # builds the Dockerfile and deploys
```

Then set the variables (Railway dashboard → Variables, or `railway variables set KEY=VALUE`). Use
[`.env.example`](.env.example) as the list. The essentials:

```
SPRING_PROFILES_ACTIVE=production
SPRING_DATASOURCE_URL=jdbc:postgresql://<pooled-neon-host>/nest_crm?sslmode=require
SPRING_DATASOURCE_USERNAME=<app-role>
SPRING_DATASOURCE_PASSWORD=<secret>
MIGRATION_JDBC_URL=jdbc:postgresql://<direct-neon-host>/nest_crm?sslmode=require
MIGRATION_DB_USERNAME=<migration-role>
MIGRATION_DB_PASSWORD=<secret>
APP_BASE_URL=https://<your-railway-domain>
SESSION_COOKIE_SECURE=true
DB_POOL_MAX=5
# One-time owner bootstrap (set, deploy, then set APP_OWNER_BOOTSTRAP=false and redeploy):
APP_OWNER_BOOTSTRAP=true
APP_OWNER_EMAIL=you@example.com
APP_OWNER_PASSWORD=<a-strong-password>
# AI coach stays on the mock until you choose a provider:
AI_ENABLED=false
```

Railway injects `PORT` automatically — do not set it.

## 3. Health check

Railway → service **Settings → Health Check Path**: `/actuator/health`. The app exposes it
unauthenticated for the probe.

## 4. First boot + verify

1. Deploy. On startup Flyway runs V1–V5 against the **direct** connection and the owner is bootstrapped.
2. Open `https://<your-railway-domain>/` and sign in with the bootstrap email/password.
3. Set `APP_OWNER_BOOTSTRAP=false` and redeploy (so it never re-bootstraps).
4. Verify: create a lead, redeploy, confirm it persists; open a second browser session and confirm
   scoped access; check `/actuator/health` is `UP`.

## 5. Notes / guardrails

- Keep **staging and production** as separate Neon databases/roles. Staging uses synthetic data.
- Migrations are backward-compatible; **reverting a container image does not reverse a DB migration**.
- Health checks are not continuous uptime monitoring — add monitoring separately.
- The background workers (reminders + outbox) run in the service (`SCHEDULING_ENABLED=true`). With a
  single replica this is fine; the outbox claims rows with `FOR UPDATE SKIP LOCKED`, so multiple
  replicas are also safe.
- Outbound messaging is **simulated** (`DELIVERY_PROVIDER=simulated`) until you wire and verify a real
  channel — nothing is actually sent.
- Before enabling a real AI provider, set a spend cap and confirm permitted customer-data handling.

## Git push

This repo has no Git remote yet and `gh` is not installed, so the push needs a remote you create:

```bash
# create an EMPTY private repo on github.com first (e.g. nest-crm), then:
git remote add origin https://github.com/<you>/nest-crm.git
git push -u origin main
```

Everything is committed locally on `main`. If you give me the remote URL (or install+auth `gh`), I can
push for you.
