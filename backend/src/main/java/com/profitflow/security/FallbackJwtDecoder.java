package com.profitflow.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Tries the primary {@link JwtDecoder} first, then an optional secondary decoder.
 * Used during RSA key rotation: tokens signed with the previous key still verify
 * until they expire, while new tokens use the current key.
 */
public class FallbackJwtDecoder implements JwtDecoder {

    private final JwtDecoder primary;
    private final JwtDecoder secondary;

    public FallbackJwtDecoder(JwtDecoder primary, JwtDecoder secondary) {
        this.primary   = primary;
        this.secondary = secondary;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            return primary.decode(token);
        } catch (JwtException primaryFailure) {
            if (secondary == null) {
                throw primaryFailure;
            }
            try {
                return secondary.decode(token);
            } catch (JwtException ignored) {
                throw primaryFailure;
            }
        }
    }
}
