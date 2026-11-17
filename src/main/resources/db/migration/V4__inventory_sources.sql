-- Milestone 3: record source ownership and permission for property inventory provenance.

ALTER TABLE property_sources ADD COLUMN source_owner TEXT;
ALTER TABLE property_sources ADD COLUMN permission TEXT DEFAULT 'UNKNOWN';

-- Fast lookup of the current/latest source per property for freshness checks.
CREATE INDEX ix_property_sources_latest
    ON property_sources (property_id, fetched_at DESC);
