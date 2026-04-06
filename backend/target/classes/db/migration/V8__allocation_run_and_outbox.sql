-- Versioned allocation run history (material calculation audit trail) + transactional outbox
-- for integration events (e.g. downstream analytics, approval workflows).

CREATE TABLE allocation_run (
    run_number          BIGSERIAL PRIMARY KEY,
    executed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    executed_by         VARCHAR(100) NOT NULL,
    input_snapshot_hash VARCHAR(64)   NOT NULL,
    result_json         TEXT          NOT NULL
);

CREATE INDEX idx_allocation_run_executed_at ON allocation_run (executed_at DESC);

CREATE TABLE domain_event_outbox (
    id            UUID PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    payload_json  TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ
);

CREATE INDEX idx_domain_event_outbox_unpublished ON domain_event_outbox (created_at);
