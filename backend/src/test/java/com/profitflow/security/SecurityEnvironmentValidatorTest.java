package com.profitflow.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SecurityEnvironmentValidator}.
 *
 * <p>Directly constructs instances without Spring context to exercise all
 * validation branches for both production and non-production profiles.
 * Environment variable checks ({@code DB_PASSWORD}, etc.) are inherently
 * best-effort in unit tests since env vars cannot be set per-test; the tests
 * focus on the CORS, DB URL, and profile-switching branches.
 */
class SecurityEnvironmentValidatorTest {

    private SecurityEnvironmentValidator validator(String profile, String dbUrl, String allowedOrigins) {
        return new SecurityEnvironmentValidator(profile, dbUrl, allowedOrigins, false, false);
    }

    private SecurityEnvironmentValidator validator(
            String profile, String dbUrl, String allowedOrigins,
            boolean springdocEnabled, boolean openapiUnauthenticated) {
        return new SecurityEnvironmentValidator(
                profile, dbUrl, allowedOrigins, springdocEnabled, openapiUnauthenticated);
    }

    // ── Non-production profile — should never throw ────────────────────────────

    @Test
    void nonProdWithLocalhostDbAndNoOriginsDoesNotThrow() {
        var v = validator("default", "jdbc:postgresql://localhost:5432/pfdb", "");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    @Test
    void nonProdWithWildcardOriginsDoesNotThrow() {
        var v = validator("default", "jdbc:postgresql://localhost/db", "*");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    @Test
    void nonProdWithValidRemoteDbAndOriginsDoesNotThrow() {
        var v = validator("staging", "jdbc:postgresql://db.internal:5432/pfdb",
                "https://app.example.com");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    // ── Production profile — CORS violations ──────────────────────────────────

    @Test
    void prodWithEmptyOriginsThrows() {
        var v = validator("prod", "jdbc:postgresql://db.prod:5432/pfdb", "");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALLOWED_ORIGINS");
    }

    @Test
    void prodWithWildcardOriginsThrows() {
        var v = validator("prod", "jdbc:postgresql://db.prod:5432/pfdb", "*");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ALLOWED_ORIGINS");
    }

    // ── Production profile — DB URL violations ────────────────────────────────

    @Test
    void prodWithLocalhostDbUrlThrows() {
        var v = validator("prod",
                "jdbc:postgresql://localhost:5432/pfdb",
                "https://app.example.com");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL");
    }

    @Test
    void prodWith127HostDbUrlThrows() {
        var v = validator("prod",
                "jdbc:postgresql://127.0.0.1:5432/pfdb",
                "https://app.example.com");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL");
    }

    @Test
    void prodWithValidDbAndOriginsPassesNonEnvVarChecks() {
        // prod with valid CORS + remote DB — should still throw for missing env vars
        // (DB_PASSWORD, RSA keys), but NOT for CORS or DB URL violations
        var v = validator("prod", "jdbc:postgresql://db.prod:5432/pfdb",
                "https://app.example.com");

        // This will throw for missing env vars, not CORS/DB URL
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("ALLOWED_ORIGINS")
                .hasMessageNotContaining("DB_URL");
    }

    // ── Profile detection ─────────────────────────────────────────────────────

    @Test
    void profileContainingProdSubstringTriggersProductionChecks() {
        // "prod,metrics" contains "prod" → production validation
        var v = validator("prod,metrics", "jdbc:postgresql://localhost/db", "");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stagingProfileDoesNotTriggerProductionChecks() {
        var v = validator("staging", "jdbc:postgresql://localhost/db", "");
        assertThatCode(v::validate).doesNotThrowAnyException();
    }

    @Test
    void prodWithAnonymousOpenApiDocsThrows() {
        var v = validator(
                "prod",
                "jdbc:postgresql://db.prod:5432/pfdb",
                "https://app.example.com",
                true,
                true);

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SpringDoc");
    }

    @Test
    void nonProdWithAnonymousOpenApiDoesNotThrowForThatReason() {
        var v = validator(
                "default",
                "jdbc:postgresql://localhost/db",
                "",
                true,
                true);
        assertThatCode(v::validate).doesNotThrowAnyException();
    }
}
