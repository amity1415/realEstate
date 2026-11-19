# Meta Lead Ads → CRM Inbox: setup

Connect Facebook/Instagram **Lead Ads** so new leads flow straight into the CRM Inbox,
scored and attributed. The webhook is **off until the secrets below are set** — nothing
is exposed by default.

## 1. Create the inbound source (gives you the routing token)
In the app: **Team / Settings → Add inbound source**, name it `Meta Lead Ads`, and choose
the default owner. Copy the **token** shown once — this becomes `META_SOURCE_TOKEN`.

## 2. Set Railway environment variables
| Variable | Where it comes from |
|---|---|
| `META_VERIFY_TOKEN` | Any string you choose; you'll paste the same value into Meta's dashboard. |
| `META_APP_SECRET` | Meta app → **Settings → Basic → App Secret**. Validates every delivery's signature. |
| `META_PAGE_ACCESS_TOKEN` | A **Page** access token with `leads_retrieval` permission (used to fetch each lead's fields). |
| `META_SOURCE_TOKEN` | The inbound-source token from step 1. |
| `META_GRAPH_VERSION` | Optional, defaults to `v21.0`. |

## 3. Configure the webhook in Meta
- **Callback URL:** `https://<your-app>/webhooks/meta/leads`
- **Verify token:** the same value as `META_VERIFY_TOKEN`
- Subscribe the Page to the **`leadgen`** field.

Meta calls the URL once to verify (the app echoes the challenge only if the token matches),
then POSTs each new lead. Every POST's `X-Hub-Signature-256` is checked against
`META_APP_SECRET`; unsigned or tampered payloads are rejected and never processed.

## How it flows
`leadgen` webhook → signature verified → lead fields fetched from the Graph API
→ routed through the existing inbound intake (dedupe by email, assignee notified,
attribution captured: channel/campaign/ad/form) → **scored** → appears in **Inbox**.

## Notes
- The webhook reuses the inbound source's rate limit (60/min). Very large bursts beyond
  that are logged and retried by Meta.
- Test deliveries that include inline `field_data` are ingested without a Graph call.
- Compliance: only first-party data the lead submitted on your form is stored. No scraping.
