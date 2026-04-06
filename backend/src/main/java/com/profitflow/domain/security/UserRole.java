package com.profitflow.domain.security;

/**
 * Application role hierarchy for ProfitFlow.
 *
 * <pre>
 * ANALYST          – read-only access to all data; cannot modify
 * FINANCE_MANAGER  – full read/write; can create costs, rules, run allocation
 * ADMIN            – everything + user management
 * </pre>
 *
 * Stored in the {@code app_user} table as a plain string so that new roles
 * can be added via Flyway migration without an enum ALTER TABLE.
 */
public enum UserRole {

    /** Read-only — suitable for FP&A analysts, auditors, and external reviewers. */
    ANALYST,

    /** Read + write — for finance managers who own the cost model. */
    FINANCE_MANAGER,

    /** Unrestricted — platform administrators and DevOps. */
    ADMIN
}
