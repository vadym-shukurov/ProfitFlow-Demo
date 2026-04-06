package com.profitflow.security;

import com.profitflow.adapter.out.persistence.jpa.RevokedAccessTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RevokedTokenValidator}.
 * Verifies that revoked and non-revoked JWTs are correctly handled.
 *
 * <p>Uses a real {@link TokenRevocationService} (backed by a mocked
 * {@link RevokedAccessTokenRepository}) rather than mocking the concrete service
 * class to avoid Byte Buddy incompatibility on Java 25.
 */
@ExtendWith(MockitoExtension.class)
class RevokedTokenValidatorTest {

    @Mock
    private RevokedAccessTokenRepository repository;

    private RevokedTokenValidator validator;
    private TokenRevocationService revocationService;

    @BeforeEach
    void setUp() {
        revocationService = new TokenRevocationService(repository);
        validator = new RevokedTokenValidator(revocationService);
    }

    @Test
    void validatesSuccessfullyWhenTokenIsNotRevoked() {
        when(repository.existsById("not-revoked-jti")).thenReturn(false);
        Jwt jwt = buildJwt("not-revoked-jti");

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void failsValidationWhenTokenIsRevoked() {
        // Pre-populate cache by calling revoke directly
        when(repository.save(any())).thenReturn(null);
        Instant exp = Instant.now().plusSeconds(900);
        revocationService.revoke("revoked-jti", exp, "alice", "LOGOUT");

        Jwt jwt = buildJwt("revoked-jti");
        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(e -> "token_revoked".equals(e.getErrorCode()));
    }

    @Test
    void validatesSuccessfullyWhenJtiIsNull() {
        // Token without a jti claim — treated as "not revoked" (null jti returns false)
        Jwt jwt = buildJwt(null);

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    private static Jwt buildJwt(String jti) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", "user");
        if (jti != null) {
            claims.put("jti", jti);
        }
        return new Jwt("dummy-token",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "RS256"),
                claims);
    }
}
