package com.profitflow.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Spring Security OAuth2 token validator that rejects JWTs whose {@code jti} claim
 * appears in the {@link TokenRevocationService} denylist.
 *
 * <p>This validator is added to the {@code JwtDecoder} validator chain in
 * {@link SecurityConfig} so every authenticated request is checked against the
 * revocation list before the request is processed.
 *
 * <h2>Performance</h2>
 * {@link TokenRevocationService#isRevoked(String)} checks the Caffeine in-process
 * cache first, so the per-request overhead is a single hash map lookup for the
 * common case (token not revoked).
 *
 * <h2>Security guarantee</h2>
 * When a user calls {@code POST /api/v1/auth/logout}, the current access token's
 * {@code jti} is added to the denylist. Any subsequent request with that same
 * token receives a {@code 401 Unauthorized} — immediate revocation rather than
 * waiting for the 15-minute natural expiry.
 */
public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED_ERROR = new OAuth2Error(
            "token_revoked",
            "The access token has been revoked.",
            null);

    private final TokenRevocationService revocationService;

    public RevokedTokenValidator(TokenRevocationService revocationService) {
        this.revocationService = revocationService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jti = token.getId();
        if (revocationService.isRevoked(jti)) {
            return OAuth2TokenValidatorResult.failure(REVOKED_ERROR);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
