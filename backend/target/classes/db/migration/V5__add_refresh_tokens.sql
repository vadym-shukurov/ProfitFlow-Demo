-- ── V5: Refresh token denylist ───────────────────────────────────────────────
-- Supports short-lived access tokens (15 min) paired with longer-lived refresh
-- tokens (7 days). On logout or token rotation, the refresh token is marked
-- revoked so it cannot be reused even before its natural expiry.
--
-- Security properties:
--   - token_hash stores a SHA-256 hash of the opaque UUID token — never the
--     raw token — so DB access does not expose usable credentials.
--   - Rows are kept for 30 days after expiry for forensic traceability (SOX).
--   - A partial index on (token_hash, revoked) makes validation O(1).

CREATE TABLE refresh_token (
    id            UUID        PRIMARY KEY,
    token_hash    VARCHAR(64) NOT NULL,          -- SHA-256 hex of the raw token
    username      VARCHAR(100) NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked_at    TIMESTAMPTZ,
    revoked_reason VARCHAR(100),                  -- e.g. 'LOGOUT', 'ROTATION', 'ADMIN'

    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

-- Fast lookup for validation (most queries are by token_hash where revoked = false)
CREATE INDEX idx_refresh_token_lookup
    ON refresh_token (token_hash)
    WHERE revoked = FALSE;

CREATE INDEX idx_refresh_token_username ON refresh_token (username);
CREATE INDEX idx_refresh_token_expires  ON refresh_token (expires_at);
