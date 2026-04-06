package com.profitflow.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring MVC global configuration.
 *
 * <h2>CORS policy</h2>
 * Allowed origins are configured from the {@code ALLOWED_ORIGINS} environment
 * variable (comma-separated list of origins). The Angular dev-server on
 * {@code localhost:4200} (plain HTTP on loopback) is always included for local development.
 *
 * <p>Wildcard {@code "*"} is explicitly rejected at startup — it would allow any
 * website to make cross-origin requests with credentials, defeating CSRF protection.
 *
 * <p>An explicit header whitelist is used rather than {@code allowedHeaders("*")}
 * to prevent inadvertent exposure of custom internal headers to cross-origin callers.
 */
@Configuration
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    /**
     * CORS is configured in a {@link Bean} method so validation can throw before any
     * {@link WebMvcConfigurer} instance exists (avoids CT_CONSTRUCTOR_THROW on partial init).
     */
    @Bean
    public WebMvcConfigurer corsWebMvcConfigurer(
            @Value("${profitflow.security.allowed-origins:}") String rawOrigins) {
        String[] allowedOrigins = buildOriginList(rawOrigins);
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        // Explicit header whitelist — never use "*" with credentials
                        .allowedHeaders(
                                "Authorization",
                                "Content-Type",
                                "Accept",
                                "X-Correlation-Id",
                                "X-XSRF-TOKEN")
                        .exposedHeaders("X-Correlation-Id")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses the raw comma-separated origin string into an array, always including
     * the localhost dev-server, and validating that no wildcard entries are present.
     */
    /** Package-private for unit tests in the same package. */
    static String[] buildOriginList(String rawOrigins) {
        List<String> origins = new ArrayList<>();
        // nosemgrep: html.security.plaintext-http-link.plaintext-http-link — ng serve uses http on loopback only
        origins.add("http://localhost:4200");
        // nosemgrep: html.security.plaintext-http-link.plaintext-http-link
        origins.add("http://127.0.0.1:4200");

        if (rawOrigins != null && !rawOrigins.isBlank()) {
            for (String origin : rawOrigins.split(",")) {
                String trimmed = origin.strip();
                if (trimmed.equals("*")) {
                    log.error("SECURITY VIOLATION: ALLOWED_ORIGINS contains wildcard '*'. "
                            + "Wildcards are rejected — set specific origin domains.");
                    throw new IllegalStateException(
                            "ALLOWED_ORIGINS must not contain '*'. "
                            + "Specify exact origin URLs (e.g. https://app.profitflow.example.com).");
                }
                if (!trimmed.isEmpty()) {
                    origins.add(trimmed);
                }
            }
        } else {
            log.warn("SECURITY WARNING: ALLOWED_ORIGINS is not set. "
                    + "CORS will only allow localhost:4200 and 127.0.0.1:4200 over HTTP (dev). "
                    + "Set ALLOWED_ORIGINS to your production domain.");
        }

        return origins.toArray(new String[0]);
    }
}
