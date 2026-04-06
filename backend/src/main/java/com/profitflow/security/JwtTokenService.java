package com.profitflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues signed RS256 JWT access tokens for authenticated principals.
 *
 * <h2>Token claims</h2>
 * <ul>
 *   <li>{@code jti} — unique JWT ID (UUID); enables per-token revocation if needed</li>
 *   <li>{@code kid} — JWS header key id; supports multi-key rotation ({@link RsaKeyLoader})</li>
 *   <li>{@code sub} — username</li>
 *   <li>{@code iss} — {@code profitflow-api}</li>
 *   <li>{@code aud} — {@code profitflow-api}; prevents cross-service token replay</li>
 *   <li>{@code iat} — issue time (UTC)</li>
 *   <li>{@code exp} — expiry = {@code iat + ACCESS_TOKEN_TTL_MINUTES}</li>
 *   <li>{@code roles} — space-separated Spring authority strings
 *       (e.g. {@code "ROLE_FINANCE_MANAGER"})</li>
 * </ul>
 *
 * <h2>Short-lived access tokens</h2>
 * Access tokens are valid for {@value #ACCESS_TOKEN_TTL_MINUTES} minutes.
 * Clients must use a refresh token (see {@link RefreshTokenService}) to obtain
 * a new access token without re-authenticating. This limits the blast radius of
 * a stolen access token to the TTL window.
 */
@Service
public class JwtTokenService {

    /** Access token lifetime in minutes. Short window limits stolen-token blast radius. */
    public static final long ACCESS_TOKEN_TTL_MINUTES = 15;

    /** Audience claim value — only tokens issued for this service are accepted. */
    public static final String AUDIENCE = "profitflow-api";

    private final JwtEncoder   encoder;
    private final RsaKeyLoader rsaKeyLoader;

    public JwtTokenService(JwtEncoder encoder, RsaKeyLoader rsaKeyLoader) {
        this.encoder      = encoder;
        this.rsaKeyLoader = rsaKeyLoader;
    }

    /**
     * Generates a signed JWT access token for the given {@code authentication}.
     *
     * @param authentication the successfully authenticated principal
     * @return compact JWT string suitable for use as a Bearer token
     */
    public String generateToken(Authentication authentication) {
        Instant now = Instant.now();
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())           // jti: enables per-token tracking
                .issuer(AUDIENCE)
                .audience(List.of(AUDIENCE))               // aud: prevents cross-service replay
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
                .subject(authentication.getName())
                .claim("roles", roles)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(() -> "RS256")
                .keyId(rsaKeyLoader.signingKeyId())
                .build();

        return encoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
