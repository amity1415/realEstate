-- Phase 1: lead intelligence (scoring) + marketing attribution.

-- Scores live in their own table so re-scoring never bumps the lead's optimistic-lock version.
CREATE TABLE lead_scores (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    lead_id      UUID NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces (id),
    score        INTEGER NOT NULL,
    segment      TEXT NOT NULL,
    factors      JSONB,
    computed_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_lead_scores_lead UNIQUE (lead_id)
);
CREATE INDEX ix_lead_scores_hot ON lead_scores (workspace_id, score DESC);

-- Marketing attribution: where a lead came from (campaign, ad, form, UTMs).
CREATE TABLE lead_attribution (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    lead_id        UUID NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    workspace_id   UUID NOT NULL REFERENCES workspaces (id),
    channel        TEXT,
    utm_source     TEXT,
    utm_medium     TEXT,
    utm_campaign   TEXT,
    ad_id          TEXT,
    form_id        TEXT,
    landing_url    TEXT,
    first_touch_at TIMESTAMPTZ,
    last_touch_at  TIMESTAMPTZ,
    CONSTRAINT uq_lead_attribution_lead UNIQUE (lead_id)
);
CREATE INDEX ix_lead_attribution_campaign ON lead_attribution (workspace_id, utm_campaign);
