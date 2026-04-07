package com.profitflow.security;

import com.profitflow.application.port.out.BusinessMetricsPort;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of opaque refresh tokens.
 *
 * <h2>Security design</h2>
 * <ul>
 *   <li>Tokens are cryptographically random UUIDs — 122 bits of entropy.</li>
 *   <li>Only a SHA-256 hash is stored in the database via
 *       {@link RefreshTokenRepositoryPort}, so a DB breach does not expose
 *       usable credentials.</li>
 *   <li>Refresh tokens rotate on use: the old token is revoked and a new one
 *       is issued atomically. Presenting the <em>old</em> token again after
 *       rotation triggers {@link #rotate} to revoke <strong>all</strong> refresh
 *       tokens for that user and increment a security metric (theft detection).</li>
 *   <li>Access tokens are 15 minutes; refresh tokens are 7 days.</li>
 * </ul>
 *
 * <h2>Layering</h2>
 * This service lives in the {@code security} package and depends only on
 * {@link RefreshTokenRepositoryPort} (an application-layer outbound port).
 * It does not import any JPA or adapter types, preserving the hexagonal boundary.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** Refresh token lifetime in days. */
    public static final long REFRESH_TOKEN_TTL_DAYS = 7;

    /** Retention period for revoked/expired tokens (for forensic audit). */
    private static final long RETENTION_DAYS = 30;

    private final RefreshTokenRepositoryPort repository;
    private final BusinessMetricsPort        metrics;
    private final ObjectProvider<RefreshTokenService> selfProvider;

    public RefreshTokenService(RefreshTokenRepositoryPort repository,
                               BusinessMetricsPort metrics,
                               ObjectProvider<RefreshTokenService> selfProvider) {
        this.repository = repository;
        this.metrics    = metrics;
        this.selfProvider = selfProvider;
    }

    /**
     * Issues a new refresh token for the given user.
     *
     * @param username the authenticated username
     * @return the raw (unhashed) token string — return to the client once; not stored in DB
     */
    @Transactional
    public String issue(String username) {
        String rawToken = UUID.randomUUID().toString();
        String hash     = sha256Hex(rawToken);
        Instant now     = Instant.now();

        repository.save(
                UUID.randomUUID(),
                hash,
                username,
                now,
                now.plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS));

        log.debug("Refresh token issued for user={}", username);
        return rawToken;
    }

    /**
     * Validates a refresh token and rotates it atomically.
     *
     * <p>If valid, the old token is revoked with reason {@code "ROTATION"} and a
     * new one is issued in the same transaction.
     *
     * @param rawToken the raw token string received from the client
     * @return rotation result; check {@link RotationResult#valid()} before using
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        String hash = sha256Hex(rawToken);
        return repository.findActiveByHash(hash)
                .map(view -> {
                    if (view.isExpired()) {
                        repository.revoke(view.id(), "EXPIRED");
                        log.warn("Expired refresh token presented by user={}", view.username());
                        return RotationResult.invalid("Token expired");
                    }
                    repository.revoke(view.id(), "ROTATION");
                    // Ensure @Transactional on issue(...) is invoked via the Spring proxy.
                    String newRawToken = selfProvider.getObject().issue(view.username());
                    log.debug("Refresh token rotated for user={}", view.username());
                    return RotationResult.success(view.username(), newRawToken);
                })
                .orElseGet(() -> handlePossiblyReusedToken(hash));
    }

    /**
     * If the hash matches a token already revoked with reason {@code ROTATION},
     * an attacker (or race) reused an old refresh token — revoke every session.
     */
    private RotationResult handlePossiblyReusedToken(String hash) {
        Optional<String> victim = repository.findUsernameByHashAndRevokedReason(hash, "ROTATION");
        if (victim.isPresent()) {
            String username = victim.get();
            log.error(
                    "SECURITY: refresh token reuse detected for user={} — revoking all refresh tokens",
                    username);
            metrics.recordRefreshTokenReuseDetected();
            int revoked = repository.revokeAllForUser(username, "REFRESH_REUSE");
            log.warn("Revoked {} refresh token(s) after reuse detection for user={}", revoked, username);
            return RotationResult.invalid("Invalid or already used token");
        }
        log.warn("Unknown or already-revoked refresh token presented");
        return RotationResult.invalid("Invalid or already used token");
    }

    /**
     * Revokes a single refresh token by its raw value (single-device logout).
     *
     * <p>The raw token is hashed before lookup so it is never compared directly
     * in the database. Returns {@code true} if the token was found and revoked.
     *
     * @param rawToken the raw refresh token received from the client
     * @return {@code true} if the token was found and revoked; {@code false} if
     *         the token was not found or was already revoked
     */
    @Transactional
    public boolean revokeByRawToken(String rawToken) {
        String hash  = sha256Hex(rawToken);
        int revoked  = repository.revokeByHash(hash, "LOGOUT");
        if (revoked > 0) {
            log.info("Refresh token revoked (single-device logout)");
        } else {
            log.warn("Single-device logout requested for unknown or already-revoked token");
        }
        return revoked > 0;
    }

    /**
     * Revokes all active refresh tokens for a user (logout-all-devices).
     *
     * @param username the user whose tokens should be invalidated
     * @return number of tokens revoked
     */
    @Transactional
    public int revokeAll(String username) {
        int count = repository.revokeAllForUser(username, "LOGOUT_ALL");
        log.info("Revoked {} refresh token(s) for user={} (logout-all-devices)", count, username);
        return count;
    }

    /**
     * Scheduled cleanup job — deletes revoked/expired tokens past the retention window.
     * Runs daily at 03:00 UTC.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff  = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = repository.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} expired/revoked refresh token(s) older than {} days",
                    deleted, RETENTION_DAYS);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Computes the SHA-256 hex digest of the raw token string.
     * The hash (not the raw token) is what gets stored in the database.
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest    = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /**
     * Result of a token rotation attempt.
     *
     * @param valid       whether the original token was valid
     * @param username    the authenticated username (only set when valid)
     * @param newRawToken the new refresh token to return to the client (only set when valid)
     * @param error       human-readable reason for failure (only set when invalid)
     */
    public record RotationResult(boolean valid, String username, String newRawToken, String error) {

        /** Creates a successful rotation result. */
        public static RotationResult success(String username, String newRawToken) {
            return new RotationResult(true, username, newRawToken, null);
        }

        /** Creates a failed rotation result. */
        public static RotationResult invalid(String error) {
            return new RotationResult(false, null, null, error);
        }
    }
}
