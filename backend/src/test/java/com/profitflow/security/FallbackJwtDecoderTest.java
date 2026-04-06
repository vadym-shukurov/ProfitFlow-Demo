package com.profitflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FallbackJwtDecoder}.
 */
class FallbackJwtDecoderTest {

    private static Jwt sampleJwt() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .claims(c -> c.put("sub", "u"))
                .issuedAt(Instant.now())
                .build();
    }

    @Test
    void primarySuccessSkipsSecondary() {
        Jwt jwt = sampleJwt();
        JwtDecoder primary = mock(JwtDecoder.class);
        JwtDecoder secondary = mock(JwtDecoder.class);
        when(primary.decode("tok")).thenReturn(jwt);

        Jwt out = new FallbackJwtDecoder(primary, secondary).decode("tok");

        assertThat(out).isSameAs(jwt);
        verify(secondary, never()).decode(anyString());
    }

    @Test
    void primaryFailsSecondarySucceeds() {
        Jwt jwt = sampleJwt();
        JwtDecoder primary = mock(JwtDecoder.class);
        JwtDecoder secondary = mock(JwtDecoder.class);
        when(primary.decode("tok")).thenThrow(new JwtException("bad sig"));
        when(secondary.decode("tok")).thenReturn(jwt);

        Jwt out = new FallbackJwtDecoder(primary, secondary).decode("tok");

        assertThat(out).isSameAs(jwt);
    }

    @Test
    void bothFailThrowsPrimaryException() {
        JwtDecoder primary = mock(JwtDecoder.class);
        JwtDecoder secondary = mock(JwtDecoder.class);
        JwtException primaryErr = new JwtException("primary");
        when(primary.decode("tok")).thenThrow(primaryErr);
        when(secondary.decode("tok")).thenThrow(new JwtException("secondary"));

        assertThatThrownBy(() -> new FallbackJwtDecoder(primary, secondary).decode("tok"))
                .isSameAs(primaryErr);
    }

    @Test
    void noSecondaryRethrowsPrimary() {
        JwtDecoder primary = mock(JwtDecoder.class);
        JwtException primaryErr = new JwtException("only");
        when(primary.decode("tok")).thenThrow(primaryErr);

        assertThatThrownBy(() -> new FallbackJwtDecoder(primary, null).decode("tok"))
                .isSameAs(primaryErr);
    }
}
