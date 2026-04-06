package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link RefreshTokenEntity}. */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Finds a non-revoked refresh token by its hash.
     * Uses the partial index on {@code (token_hash) WHERE revoked = false}.
     */
    @Query("SELECT t FROM RefreshTokenEntity t WHERE t.tokenHash = :hash AND t.revoked = false")
    Optional<RefreshTokenEntity> findActiveByTokenHash(@Param("hash") String tokenHash);

    /**
     * Finds a revoked token by hash and revocation reason (reuse detection after rotation).
     */
    @Query("SELECT t.username FROM RefreshTokenEntity t WHERE t.tokenHash = :hash "
         + "AND t.revoked = true AND t.revokedReason = :reason")
    Optional<String> findUsernameByHashAndRevokedReason(@Param("hash") String hash,
                                                        @Param("reason") String reason);

    /**
     * Revokes a single active refresh token by its hash (single-device logout).
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revoked = true, t.revokedAt = :now, "
         + "t.revokedReason = :reason WHERE t.tokenHash = :hash AND t.revoked = false")
    int revokeByHash(@Param("hash") String hash,
                     @Param("now") Instant now,
                     @Param("reason") String reason);

    /**
     * Revokes all active refresh tokens for a user (e.g. on password change or admin revocation).
     */
    @Modifying
    @Query("UPDATE RefreshTokenEntity t SET t.revoked = true, t.revokedAt = :now, "
         + "t.revokedReason = :reason WHERE t.username = :username AND t.revoked = false")
    int revokeAllForUser(@Param("username") String username,
                         @Param("now") Instant now,
                         @Param("reason") String reason);

    /**
     * Deletes expired tokens older than the retention cutoff.
     * Call periodically to prevent unbounded table growth.
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :cutoff AND t.revoked = true")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
