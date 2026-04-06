package com.profitflow.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting and querying refresh tokens.
 *
 * <p>Implemented by the JPA persistence adapter so that the {@code security}
 * package does not need to depend on {@code adapter.out.persistence} types,
 * preserving the hexagonal layering: security → application port →
 * persistence adapter.
 */
public interface RefreshTokenRepositoryPort {

    /**
     * Persists a new refresh token record.
     *
     * @param id          unique token ID
     * @param tokenHash   SHA-256 hex digest of the raw token
     * @param username    owning user
     * @param issuedAt    issue timestamp
     * @param expiresAt   expiry timestamp
     */
    void save(UUID id, String tokenHash, String username, Instant issuedAt, Instant expiresAt);

    /**
     * Finds an active (non-revoked) token by its hash.
     *
     * @param tokenHash SHA-256 hex digest of the raw token
     * @return matching active token, or empty if not found / already revoked
     */
    Optional<RefreshTokenView> findActiveByHash(String tokenHash);

    /**
     * If a token with this hash exists, was revoked, and the reason matches,
     * returns the owning username. Used to detect refresh-token reuse after rotation.
     *
     * @param tokenHash SHA-256 hex digest
     * @param reason    e.g. {@code "ROTATION"}
     * @return username if a matching revoked row exists
     */
    Optional<String> findUsernameByHashAndRevokedReason(String tokenHash, String reason);

    /**
     * Marks a single token as revoked by its internal ID.
     *
     * @param id     token ID returned from {@link #findActiveByHash}
     * @param reason short reason code, e.g. {@code "ROTATION"} or {@code "LOGOUT"}
     */
    void revoke(UUID id, String reason);

    /**
     * Marks a single token as revoked by its SHA-256 hash.
     * Returns the number of tokens revoked (0 if the token did not exist / was already revoked).
     *
     * @param tokenHash SHA-256 hex digest of the raw token
     * @param reason    short reason code, e.g. {@code "LOGOUT"}
     * @return 1 if revoked, 0 if not found or already revoked
     */
    int revokeByHash(String tokenHash, String reason);

    /**
     * Revokes all active tokens for a user.
     *
     * @param username  target user
     * @param reason    reason code, e.g. {@code "LOGOUT"}
     * @return number of tokens revoked
     */
    int revokeAllForUser(String username, String reason);

    /**
     * Deletes revoked/expired tokens older than the given cutoff date.
     * Called by the scheduled cleanup job to prevent unbounded table growth.
     *
     * @param cutoff tokens with {@code expiresAt < cutoff} will be deleted
     * @return number of rows deleted
     */
    int deleteExpiredBefore(Instant cutoff);

    // ── Projection ────────────────────────────────────────────────────────────

    /**
     * Read-only projection of a refresh token record.
     * Avoids exposing JPA entity types outside the persistence adapter.
     *
     * @param id        token ID
     * @param username  owning user
     * @param expiresAt expiry timestamp
     */
    record RefreshTokenView(UUID id, String username, Instant expiresAt) {

        /** Returns {@code true} if the token is past its expiry time. */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
