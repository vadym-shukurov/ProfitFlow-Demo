package com.profitflow.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a revoked JWT access token in the denylist.
 *
 * <p>Access tokens are short-lived (15 min), so rows in this table are transient.
 * The scheduled cleanup job in {@code TokenRevocationService} removes rows once
 * {@code expiresAt} has passed.
 *
 * <p>The primary key is the {@code jti} claim from the JWT, which is a UUID string
 * generated at token issuance time.
 */
@Entity
@Table(name = "revoked_access_tokens")
public class RevokedAccessTokenEntity {

    /** JWT ID (jti claim) — natural primary key, never null. */
    @Id
    @Column(name = "jti", length = 36, nullable = false, updatable = false)
    private String jti;

    /** Timestamp when the token was explicitly revoked. */
    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt;

    /** Original expiry of the JWT — used by the cleanup job. */
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Username that owned the token — stored for audit trail. */
    @Column(name = "username", length = 100, nullable = false, updatable = false)
    private String username;

    /** Short reason code, e.g. {@code "LOGOUT"} or {@code "LOGOUT_ALL"}. */
    @Column(name = "reason", length = 50, nullable = false, updatable = false)
    private String reason;

    protected RevokedAccessTokenEntity() {
    }

    public RevokedAccessTokenEntity(String jti, Instant revokedAt, Instant expiresAt,
                                    String username, String reason) {
        this.jti       = jti;
        this.revokedAt = revokedAt;
        this.expiresAt = expiresAt;
        this.username  = username;
        this.reason    = reason;
    }

    public String getJti() {
        return jti;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getUsername() {
        return username;
    }

    public String getReason() {
        return reason;
    }
}
