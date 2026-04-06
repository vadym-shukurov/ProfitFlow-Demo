package com.profitflow.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.profitflow.adapter.out.persistence.entity.RevokedAccessTokenEntity;
import com.profitflow.adapter.out.persistence.jpa.RevokedAccessTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Manages the access-token JTI denylist for immediate revocation on logout.
 *
 * <h2>Why a JTI denylist?</h2>
 * JWT access tokens are self-contained: once issued, they cannot be invalidated
 * server-side without a denylist. Without this service, a stolen access token
 * remains valid until its 15-minute TTL expires — unacceptable for logout flows
 * in a finance application where a session must end immediately.
 *
 * <h2>Performance design</h2>
 * Checking the denylist on every authenticated request must be fast.
 * This service uses a <strong>Caffeine in-process cache</strong> keyed by {@code jti}:
 * <ul>
 *   <li>Cache TTL = access token TTL (15 min) — entries expire automatically.</li>
 *   <li>Maximum size = 10,000 entries — bounded to prevent unbounded memory growth.</li>
 *   <li>On a cache miss the DB is queried once, then the result is cached.</li>
 * </ul>
 *
 * <h2>Durability</h2>
 * Revocations are persisted to the {@code revoked_access_tokens} table so that
 * a server restart does not "un-revoke" tokens. On startup, the cache is cold
 * but the DB query catches any tokens revoked during the downtime.
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    /** Maximum in-memory denylist entries (prevents unbounded growth under DoS). */
    static final int CACHE_MAX_SIZE = 10_000;

    /** Cache TTL mirrors the access-token TTL so entries expire naturally. */
    static final Duration CACHE_TTL = Duration.ofMinutes(JwtTokenService.ACCESS_TOKEN_TTL_MINUTES);

    /** Revoked JTI cache: key=jti, value=true (revoked). */
    private final Cache<String, Boolean> revokedJtiCache;
    private final RevokedAccessTokenRepository repository;

    public TokenRevocationService(RevokedAccessTokenRepository repository) {
        this.repository = repository;
        this.revokedJtiCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_SIZE)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    /**
     * Adds the given JWT's {@code jti} to the denylist.
     *
     * <p>The entry is persisted to the database AND inserted into the in-memory
     * cache. Subsequent calls to {@link #isRevoked(String)} return {@code true}
     * immediately (from cache) for the duration of the token's remaining TTL.
     *
     * @param jti       the {@code jti} claim from the access token
     * @param expiresAt the access token's {@code exp} claim — used for cache TTL
     *                  alignment and DB cleanup
     * @param username  the token owner — stored for audit trail
     * @param reason    short reason code, e.g. {@code "LOGOUT"} or {@code "LOGOUT_ALL"}
     */
    @Transactional
    public void revoke(String jti, Instant expiresAt, String username, String reason) {
        if (jti == null || jti.isBlank()) {
            log.warn("Attempted to revoke a token with null/blank jti — skipped");
            return;
        }
        repository.save(new RevokedAccessTokenEntity(
                jti, Instant.now(), expiresAt, username, reason));
        revokedJtiCache.put(jti, Boolean.TRUE);
        log.info("Access token revoked jti={} user={} reason={}", jti, username, reason);
    }

    /**
     * Returns {@code true} if the given {@code jti} has been explicitly revoked.
     *
     * <p>The Caffeine cache is checked first (O(1), in-process). Only on a cache
     * miss is the database consulted — this covers tokens revoked by another
     * instance (multi-node) or after a server restart.
     *
     * @param jti the {@code jti} claim from the incoming JWT
     * @return {@code true} if the token should be rejected
     */
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Boolean cached = revokedJtiCache.getIfPresent(jti);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        boolean inDb = repository.existsById(jti);
        if (inDb) {
            revokedJtiCache.put(jti, Boolean.TRUE);
        }
        return inDb;
    }

    /**
     * Scheduled cleanup job — removes expired denylist entries from the database.
     * The Caffeine cache auto-expires entries; only the DB needs explicit pruning.
     * Runs daily at 02:30 UTC to avoid overlapping with the refresh-token cleanup
     * at 03:00 UTC.
     */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void purgeExpiredRevocations() {
        int deleted = repository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired access-token revocation entries", deleted);
        }
    }
}
