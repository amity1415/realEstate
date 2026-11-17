-- Milestone 4: team notifications, a durable outbox, and approved inbound lead sources.

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    workspace_id UUID NOT NULL REFERENCES workspaces (id),
    recipient_id UUID NOT NULL REFERENCES users (id),
    type         TEXT NOT NULL,
    title        TEXT NOT NULL,
    body         TEXT,
    lead_id      UUID REFERENCES leads (id) ON DELETE CASCADE,
    task_id      UUID REFERENCES tasks (id) ON DELETE CASCADE,
    read_at      TIMESTAMPTZ,
    dedupe_key   TEXT
);
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, read_at, created_at DESC);
CREATE UNIQUE INDEX uq_notifications_dedupe
    ON notifications (workspace_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE outbox_messages (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    workspace_id  UUID NOT NULL REFERENCES workspaces (id),
    lead_id       UUID REFERENCES leads (id) ON DELETE CASCADE,
    channel       TEXT NOT NULL,
    recipient     TEXT NOT NULL,
    subject       TEXT,
    body          TEXT NOT NULL,
    status        TEXT NOT NULL,
    attempts      INTEGER NOT NULL DEFAULT 0,
    max_attempts  INTEGER NOT NULL DEFAULT 5,
    scheduled_at  TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    sent_at       TIMESTAMPTZ,
    simulated     BOOLEAN NOT NULL DEFAULT FALSE,
    dedupe_key    TEXT,
    error_message TEXT,
    created_by    UUID NOT NULL REFERENCES users (id)
);
CREATE INDEX ix_outbox_due ON outbox_messages (status, scheduled_at);
CREATE INDEX ix_outbox_lead ON outbox_messages (lead_id, created_at DESC);
CREATE UNIQUE INDEX uq_outbox_dedupe
    ON outbox_messages (workspace_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE inbound_sources (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    workspace_id     UUID NOT NULL REFERENCES workspaces (id),
    name             TEXT NOT NULL,
    token_hash       TEXT NOT NULL,
    default_owner_id UUID REFERENCES users (id),
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       UUID NOT NULL REFERENCES users (id),
    CONSTRAINT uq_inbound_token UNIQUE (token_hash)
);
CREATE INDEX ix_inbound_sources_workspace ON inbound_sources (workspace_id);
