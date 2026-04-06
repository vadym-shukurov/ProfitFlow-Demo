-- ── V2: Application users + account lockout ─────────────────────────────────
-- Passwords are stored as BCrypt hashes (cost 12). Plain-text passwords are
-- never stored. The locked_until column implements a time-based lockout policy.

CREATE TABLE app_user (
    id                    UUID PRIMARY KEY,
    username              VARCHAR(100)  NOT NULL,
    email                 VARCHAR(255)  NOT NULL,
    password_hash         VARCHAR(255)  NOT NULL,
    role                  VARCHAR(50)   NOT NULL,
    enabled               BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_attempts INT           NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT uq_user_email    UNIQUE (email),
    CONSTRAINT chk_user_role    CHECK (role IN ('ANALYST', 'FINANCE_MANAGER', 'ADMIN'))
);

CREATE INDEX idx_user_username ON app_user (username);
