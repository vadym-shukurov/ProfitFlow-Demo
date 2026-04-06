package com.profitflow.adapter.out.persistence.entity;

import com.profitflow.domain.security.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent representation of a ProfitFlow application user.
 *
 * <p>Passwords are stored as BCrypt hashes (cost factor 12) — never in plaintext.
 * The {@code lockedUntil} field implements a time-limited account lockout policy:
 * after {@code MAX_FAILED_ATTEMPTS} consecutive failures the account is locked
 * for {@code LOCKOUT_MINUTES} minutes, defending against credential-stuffing attacks.
 */
@Entity
@Table(name = "app_user")
public class AppUserEntity {

    /** Maximum consecutive login failures before lockout. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Lock duration in minutes after exceeding {@link #MAX_FAILED_ATTEMPTS}. */
    public static final int LOCKOUT_MINUTES = 15;

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppUserEntity() {
    }

    public AppUserEntity(UUID id, String username, String email,
                         String passwordHash, UserRole role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Returns {@code true} if the account is currently locked (lockout window active). */
    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /** Records a failed login attempt; locks the account if threshold is exceeded. */
    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60L);
        }
    }

    /** Resets the failed-login counter and clears any active lockout on success. */
    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
}
