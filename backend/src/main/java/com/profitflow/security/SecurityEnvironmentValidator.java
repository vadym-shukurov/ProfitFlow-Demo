package com.profitflow.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast startup validator for security-critical environment variables.
 *
 * <h2>What it checks</h2>
 * In the {@code prod} profile the following are enforced:
 * <ul>
 *   <li>{@code DB_USERNAME}, {@code DB_PASSWORD} — must be explicitly set;
 *       the application refuses to start with default credentials.</li>
 *   <li>{@code DB_URL} — must not point to localhost (checked via
 *       {@link #validateDbUrl}).</li>
 *   <li>{@code RSA_PRIVATE_KEY_PEM}, {@code RSA_PUBLIC_KEY_PEM} — JWT signing keys must
 *       come from the environment, not the bundled classpath certs.</li>
 *   <li>{@code ALLOWED_ORIGINS} — CORS origin must be a real domain, not empty or a
 *       wildcard, to prevent cross-site credential leakage.</li>
 *   <li>SpringDoc — anonymous access to {@code /v3/api-docs} / Swagger UI is forbidden
 *       in production ({@code springdoc.api-docs.enabled=true} together with
 *       {@code profitflow.security.openapi-unauthenticated-access=true}).</li>
 * </ul>
 *
 * <p>In non-production profiles, warnings are logged for any missing variables but
 * startup is not blocked.
 */
@Component
public class SecurityEnvironmentValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityEnvironmentValidator.class);

    private final String  activeProfiles;
    private final String  dbUrl;
    private final String  allowedOrigins;
    private final boolean springdocApiDocsEnabled;
    private final boolean openapiUnauthenticatedAccess;

    public SecurityEnvironmentValidator(
            @Value("${spring.profiles.active:default}")     String activeProfiles,
            @Value("${spring.datasource.url:}")             String dbUrl,
            @Value("${profitflow.security.allowed-origins:}") String allowedOrigins,
            @Value("${springdoc.api-docs.enabled:false}")    boolean springdocApiDocsEnabled,
            @Value("${profitflow.security.openapi-unauthenticated-access:false}")
            boolean openapiUnauthenticatedAccess) {
        this.activeProfiles               = activeProfiles;
        this.dbUrl                        = dbUrl;
        this.allowedOrigins               = allowedOrigins;
        this.springdocApiDocsEnabled      = springdocApiDocsEnabled;
        this.openapiUnauthenticatedAccess = openapiUnauthenticatedAccess;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void validate() {
        boolean isProd = activeProfiles != null && activeProfiles.contains("prod");

        List<String> violations = new ArrayList<>();
        List<String> warnings   = new ArrayList<>();

        checkEnvVar("DB_USERNAME", isProd, violations, warnings);
        checkEnvVar("DB_PASSWORD", isProd, violations, warnings);
        checkEnvVar("RSA_PRIVATE_KEY_PEM", isProd, violations, warnings);
        checkEnvVar("RSA_PUBLIC_KEY_PEM",  isProd, violations, warnings);

        validateAllowedOrigins(isProd, violations, warnings);
        validateDbUrl(isProd, violations, warnings);
        validateNoAnonymousOpenApiInProd(isProd, violations);

        for (String w : warnings) {
            log.warn("SECURITY WARNING: {}", w);
        }

        if (!violations.isEmpty()) {
            String msg = "Application startup blocked due to security violations in 'prod' profile:\n  - "
                    + String.join("\n  - ", violations);
            log.error(msg);
            throw new IllegalStateException(msg);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void checkEnvVar(String name, boolean isProd,
                                    List<String> violations, List<String> warnings) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            if (isProd) {
                violations.add(name + " environment variable is required in production but not set.");
            } else {
                warnings.add(name + " is not set. Using default/bundled value (acceptable for "
                        + "local dev only).");
            }
        }
    }

    private void validateAllowedOrigins(boolean isProd,
                                         List<String> violations, List<String> warnings) {
        if (allowedOrigins.isBlank() || allowedOrigins.equals("*")) {
            if (isProd) {
                violations.add("ALLOWED_ORIGINS must be set to a specific domain in production "
                        + "(got: '" + allowedOrigins + "'). An empty or wildcard origin allows "
                        + "any website to make cross-origin requests.");
            } else {
                warnings.add("ALLOWED_ORIGINS is not set. CORS will only allow localhost:4200. "
                        + "Set ALLOWED_ORIGINS in production.");
            }
        }
    }

    private void validateDbUrl(boolean isProd,
                                List<String> violations, List<String> warnings) {
        if (dbUrl.contains("localhost") || dbUrl.contains("127.0.0.1")) {
            if (isProd) {
                violations.add("DB_URL appears to point to localhost ('" + dbUrl + "'). "
                        + "Production must use a dedicated database host.");
            } else {
                warnings.add("DB_URL points to localhost — this is fine for local development.");
            }
        }
    }

    private void validateNoAnonymousOpenApiInProd(boolean isProd, List<String> violations) {
        if (isProd && springdocApiDocsEnabled && openapiUnauthenticatedAccess) {
            violations.add(
                    "Production must not expose SpringDoc/Swagger UI without authentication. "
                    + "Disable profitflow.security.openapi-unauthenticated-access (default false) "
                    + "or set springdoc.api-docs.enabled=false. Use an authenticated ADMIN session "
                    + "to read /v3/api-docs, or rely on offline contract export in CI.");
        }
    }
}
