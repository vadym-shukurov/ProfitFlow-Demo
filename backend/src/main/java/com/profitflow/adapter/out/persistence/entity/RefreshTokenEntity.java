package com.profitflow.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent refresh token record.
 *
 * <p>Only a SHA-256 hash of the raw token is stored; even if an attacker gains
 * read access to the database, they cannot reconstruct usable tokens.
 *
 * <p>Rows are retained for 30 days after expiry for SOX audit purposes.
 * A scheduled job (or pg_cron) should purge rows older than 30 days.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshTokenEntity {

    @Id
    private UUID id;

    /** SHA-256 hex digest of the raw opaque token UUID string. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    protected RefreshTokenEntity() {}

    public RefreshTokenEntity(UUID id, String tokenHash, String username,
                              Instant issuedAt, Instant expiresAt) {
        this.id        = id;
        this.tokenHash = tokenHash;
        this.username  = username;
        this.issuedAt  = issuedAt;
        this.expiresAt = expiresAt;
        this.revoked   = false;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getUsername() {
        return username;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    /**
     * Marks this token as revoked.
     *
     * @param reason short reason code, e.g. {@code "LOGOUT"}, {@code "ROTATION"}
     */
    public void revoke(String reason) {
        this.revoked       = true;
        this.revokedAt     = Instant.now();
        this.revokedReason = reason;
    }

    /** Returns {@code true} if the token is past its expiry time. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
