-- ── V7: Audit log DDL-level immutability ─────────────────────────────────────
-- Adds a PostgreSQL trigger to enforce the "never update or delete audit rows"
-- policy at the database level, not just by application convention.
--
-- This provides defence-in-depth: even a compromised DB account or a rogue
-- DML statement cannot silently alter audit history. Any attempt to UPDATE
-- or DELETE an audit_log row will raise an exception and be rejected.

CREATE OR REPLACE FUNCTION audit_log_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION
        'audit_log rows are immutable: UPDATE and DELETE are prohibited. '
        'Row id=% cannot be modified.',
        OLD.id;
END;
$$;

CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

CREATE TRIGGER trg_audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();
