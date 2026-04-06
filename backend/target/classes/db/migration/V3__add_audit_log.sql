-- ── V3: Immutable audit log ──────────────────────────────────────────────────
-- Records every state-changing API call. Once written, rows are NEVER deleted
-- or updated (enforced by application policy; no DDL prevents it, but the
-- AuditLogEntityRepository exposes no delete methods).

CREATE TABLE audit_log (
    id             UUID        PRIMARY KEY,
    timestamp      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    username       VARCHAR(100) NOT NULL,
    ip_address     VARCHAR(45),
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      VARCHAR(255),
    details        VARCHAR(2000),
    correlation_id VARCHAR(36)
);

CREATE INDEX idx_audit_timestamp  ON audit_log (timestamp DESC);
CREATE INDEX idx_audit_username   ON audit_log (username);
CREATE INDEX idx_audit_action     ON audit_log (action);
