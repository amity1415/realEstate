-- Milestone 2: on-demand AI coach. Stores grounded coaching results and advisor feedback.
-- coaching_runs also serves as the usage ledger (token counts + estimated cost per run).

CREATE TABLE coaching_runs (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    workspace_id       UUID NOT NULL REFERENCES workspaces (id),
    lead_id            UUID NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    input_hash         TEXT NOT NULL,
    input_lead_version BIGINT NOT NULL,
    status             TEXT NOT NULL,
    provider           TEXT NOT NULL,
    model              TEXT NOT NULL,
    prompt_version     TEXT NOT NULL,
    live_provider      BOOLEAN NOT NULL DEFAULT FALSE,
    result_json        JSONB,
    input_tokens       INTEGER,
    output_tokens      INTEGER,
    estimated_cost_usd NUMERIC(12, 6) DEFAULT 0,
    idempotency_key    TEXT,
    error_message      TEXT
);
CREATE INDEX ix_coaching_runs_lead ON coaching_runs (lead_id, created_at DESC);
CREATE INDEX ix_coaching_runs_usage ON coaching_runs (workspace_id, created_at);
CREATE UNIQUE INDEX uq_coaching_runs_idem
    ON coaching_runs (workspace_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_coaching_runs_cache ON coaching_runs (workspace_id, lead_id, input_hash, status);

CREATE TABLE coaching_feedback (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    run_id       UUID NOT NULL REFERENCES coaching_runs (id) ON DELETE CASCADE,
    advisor_id   UUID NOT NULL,
    helpful      BOOLEAN,
    edited       BOOLEAN NOT NULL DEFAULT FALSE,
    action_taken TEXT,
    comment      TEXT
);
CREATE INDEX ix_coaching_feedback_run ON coaching_feedback (run_id);
