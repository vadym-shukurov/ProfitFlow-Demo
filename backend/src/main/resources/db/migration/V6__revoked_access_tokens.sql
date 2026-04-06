-- ── V6: Revoked access-token JTI denylist ────────────────────────────────────
-- Stores the JWT ID (jti claim) of access tokens that have been explicitly
-- revoked before their natural expiry (e.g. on logout).
--
-- Checking this table on every authenticated request adds a DB round-trip.
-- In production, the application keeps an in-process Caffeine cache keyed by
-- jti (TTL = access-token TTL = 15 min) so lookups are sub-millisecond for
-- the common case; the DB row is only consulted after a cache miss (rare).
--
-- Rows are automatically removed by the TokenRevocationService cleanup job
-- once expires_at has passed — no retention is needed because an expired token
-- cannot be replayed regardless of whether the DB row exists.

CREATE TABLE revoked_access_tokens (
    jti        VARCHAR(36)  PRIMARY KEY,
    revoked_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ  NOT NULL,
    username   VARCHAR(100) NOT NULL,
    reason     VARCHAR(50)  NOT NULL DEFAULT 'LOGOUT'
);

-- Index for the cleanup job: DELETE WHERE expires_at < NOW()
CREATE INDEX idx_revoked_access_expires ON revoked_access_tokens (expires_at);
