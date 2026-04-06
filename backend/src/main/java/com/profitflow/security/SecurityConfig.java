package com.profitflow.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;

/**
 * Central Spring Security configuration.
 *
 * <h2>Auth flow</h2>
 * <ol>
 *   <li>Client POSTs credentials to {@code POST /api/v1/auth/login}.</li>
 *   <li>Server authenticates via {@link AppUserDetailsService}, issues a signed
 *       RS256 access JWT (15 min) and an opaque refresh token (7 days), and
 *       returns both in the response body.</li>
 *   <li>Client includes the access token as {@code Authorization: Bearer <token>}.</li>
 *   <li>On 401, the client posts the refresh token to {@code POST /api/v1/auth/refresh}
 *       to obtain a new access token without re-authenticating.</li>
 *   <li>On logout the client posts to {@code POST /api/v1/auth/logout} to revoke
 *       the refresh token server-side.</li>
 * </ol>
 *
 * <h2>Role model</h2>
 * <ul>
 *   <li>{@code ANALYST} — read-only (all GET endpoints)</li>
 *   <li>{@code FINANCE_MANAGER} — read + write (all API endpoints)</li>
 *   <li>{@code ADMIN} — admin-only operations + all of the above</li>
 * </ul>
 *
 * <h2>Security headers</h2>
 * Every response carries: {@code X-Frame-Options: DENY},
 * {@code X-Content-Type-Options: nosniff}, {@code Content-Security-Policy},
 * {@code Strict-Transport-Security}, {@code Referrer-Policy}, and
 * {@code Permissions-Policy}. OpenAPI/Swagger UI requires {@code ADMIN} when enabled unless
 * {@code profitflow.security.openapi-unauthenticated-access=true} (CI export only).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
            + "microphone=(), payment=(), usb=()";

    private final RateLimitingFilter                 rateLimitingFilter;
    private final CorrelationIdFilter                correlationIdFilter;
    private final SensitiveApiCacheHeaderFilter      sensitiveApiCacheHeaderFilter;
    private final boolean                            openapiUnauthenticatedAccess;

    public SecurityConfig(
            RateLimitingFilter rateLimitingFilter,
            CorrelationIdFilter correlationIdFilter,
            SensitiveApiCacheHeaderFilter sensitiveApiCacheHeaderFilter,
            @Value("${profitflow.security.openapi-unauthenticated-access:false}")
            boolean openapiUnauthenticatedAccess) {
        this.rateLimitingFilter           = rateLimitingFilter;
        this.correlationIdFilter          = correlationIdFilter;
        this.sensitiveApiCacheHeaderFilter = sensitiveApiCacheHeaderFilter;
        this.openapiUnauthenticatedAccess = openapiUnauthenticatedAccess;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // ── Session management ──────────────────────────────────────
                // Stateless JWT — no HttpSession, no cookies
                // Keep CSRF protection enabled by default, but ignore stateless API endpoints
                // (JWT bearer auth; no cookies). This avoids accidental CSRF disablement for any
                // future browser/session endpoints.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                ))
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Endpoint authorisation ──────────────────────────────────
                .authorizeHttpRequests(this::authorizeHttpRequests)

                // ── JWT resource server ─────────────────────────────────────
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))

                // ── Security headers ────────────────────────────────────────
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .addHeaderWriter((request, response) ->
                                response.setHeader("Permissions-Policy", PERMISSIONS_POLICY))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31_536_000)
                                .includeSubDomains(true)
                                .preload(true))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                + "script-src 'self'; "
                                + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                + "font-src 'self' https://fonts.gstatic.com; "
                                + "img-src 'self' data:; "
                                + "frame-ancestors 'none'; "
                                + "base-uri 'self'; "
                                + "form-action 'self'"))
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )

                // ── Custom filters ──────────────────────────────────────────
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(sensitiveApiCacheHeaderFilter, CorrelationIdFilter.class)
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    private void authorizeHttpRequests(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll();
        if (openapiUnauthenticatedAccess) {
            auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html").permitAll();
        } else {
            auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html").hasRole("ADMIN");
        }
        auth.requestMatchers("/actuator/health", "/actuator/info",
                "/actuator/health/liveness",
                "/actuator/health/readiness").permitAll();
        auth.requestMatchers("/actuator/prometheus", "/actuator/metrics",
                "/actuator/caches").hasRole("ADMIN");
        auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                .hasAnyRole("ANALYST", "FINANCE_MANAGER", "ADMIN");
        auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout/all")
                .hasAnyRole("ANALYST", "FINANCE_MANAGER", "ADMIN");
        auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
        auth.requestMatchers(HttpMethod.GET, "/api/v1/**")
                .hasAnyRole("ANALYST", "FINANCE_MANAGER", "ADMIN");
        auth.requestMatchers(HttpMethod.POST, "/api/v1/ai/suggest")
                .hasAnyRole("ANALYST", "FINANCE_MANAGER", "ADMIN");
        auth.requestMatchers("/api/v1/**")
                .hasAnyRole("FINANCE_MANAGER", "ADMIN");
        auth.anyRequest().denyAll();
    }

    /**
     * Maps the JWT {@code roles} claim (space-separated authority strings) back into
     * Spring Security {@link org.springframework.security.core.GrantedAuthority} objects.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("roles");
        converter.setAuthorityPrefix("");  // prefix already in the token
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }

    /**
     * JWT decoder that validates signature, expiry, issuer, audience, AND revocation.
     *
     * <p>Validator chain (all must pass):
     * <ol>
     *   <li>Issuer ({@code iss}) — prevents tokens from other systems being replayed here.</li>
     *   <li>Audience ({@code aud}) — prevents cross-service token replay.</li>
     *   <li>Revocation ({@code jti} denylist) — immediately rejects tokens explicitly
     *       revoked at logout, rather than waiting for the 15-minute natural expiry.</li>
     * </ol>
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(RsaKeyLoader keyLoader,
                                 TokenRevocationService revocationService) {
        NimbusJwtDecoder primary = buildJwtDecoder(keyLoader.publicKey(), revocationService);
        return keyLoader.previousVerificationPublicKey()
                .<JwtDecoder>map(prev -> new FallbackJwtDecoder(primary,
                        buildJwtDecoder(prev, revocationService)))
                .orElse(primary);
    }

    private static NimbusJwtDecoder buildJwtDecoder(RSAPublicKey publicKey,
                                                    TokenRevocationService revocationService) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(JwtTokenService.AUDIENCE);
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<Collection<String>>("aud",
                        aud -> aud != null && aud.contains(JwtTokenService.AUDIENCE));
        OAuth2TokenValidator<Jwt> revocationValidator =
                new RevokedTokenValidator(revocationService);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator, audienceValidator, revocationValidator));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtEncoder jwtEncoder(RsaKeyLoader keyLoader) {
        RSAKey jwk = keyLoader.signingJwk();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost factor 12 — ~250ms on modern hardware; intentionally slow for brute-force defence
        return new BCryptPasswordEncoder(12);
    }
}
