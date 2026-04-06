package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.RevokedAccessTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Spring Data JPA repository for the access-token JTI denylist.
 *
 * <p>Used only by {@code TokenRevocationService} — not exposed to any other
 * layer. The Caffeine cache in that service ensures the {@code existsById} check
 * never hits the database for tokens that are already in the hot-path cache.
 */
@Repository
public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessTokenEntity, String> {

    /**
     * Deletes all denylist entries whose tokens have already expired.
     * Called by the scheduled cleanup job; no risk of removing "live" entries
     * because an expired token is invalid regardless of the denylist.
     *
     * @param cutoff entries with {@code expires_at < cutoff} are deleted
     * @return number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM RevokedAccessTokenEntity r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
