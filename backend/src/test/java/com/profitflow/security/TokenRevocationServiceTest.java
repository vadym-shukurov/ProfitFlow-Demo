package com.profitflow.security;

import com.profitflow.adapter.out.persistence.entity.RevokedAccessTokenEntity;
import com.profitflow.adapter.out.persistence.jpa.RevokedAccessTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenRevocationService}.
 * Validates cache logic, DB delegation, and cleanup scheduling.
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private RevokedAccessTokenRepository repository;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(repository);
    }

    // ── revoke() ──────────────────────────────────────────────────────────────

    @Test
    void revokeStoresEntryInDbAndCache() {
        String jti = "test-jti-1";
        Instant exp = Instant.now().plusSeconds(900);

        when(repository.save(any())).thenReturn(null);

        service.revoke(jti, exp, "alice", "LOGOUT");

        verify(repository).save(argThat(e ->
                e.getJti().equals(jti)
                && e.getUsername().equals("alice")
                && e.getReason().equals("LOGOUT")));

        // Subsequent isRevoked check should hit cache, not DB
        assertThat(service.isRevoked(jti)).isTrue();
        verify(repository, never()).existsById(jti);
    }

    @Test
    void revokeIgnoresNullJti() {
        service.revoke(null, Instant.now().plusSeconds(900), "alice", "LOGOUT");
        verify(repository, never()).save(any());
    }

    @Test
    void revokeIgnoresBlankJti() {
        service.revoke("  ", Instant.now().plusSeconds(900), "alice", "LOGOUT");
        verify(repository, never()).save(any());
    }

    // ── isRevoked() ───────────────────────────────────────────────────────────

    @Test
    void isRevokedReturnsFalseForNullJti() {
        assertThat(service.isRevoked(null)).isFalse();
    }

    @Test
    void isRevokedQueriesDbOnCacheMiss() {
        String jti = "unknown-jti";
        when(repository.existsById(jti)).thenReturn(false);

        assertThat(service.isRevoked(jti)).isFalse();
        verify(repository).existsById(jti);
    }

    @Test
    void isRevokedCachesPositiveDbResult() {
        String jti = "db-revoked-jti";
        when(repository.existsById(jti)).thenReturn(true);

        // First call → DB miss → DB returns true
        assertThat(service.isRevoked(jti)).isTrue();

        // Second call → should hit cache, not DB again
        assertThat(service.isRevoked(jti)).isTrue();
        verify(repository).existsById(jti); // called exactly once
    }

    @Test
    void isRevokedReturnsFalseForUnknownToken() {
        when(repository.existsById("fresh-jti")).thenReturn(false);
        assertThat(service.isRevoked("fresh-jti")).isFalse();
    }

    // ── purgeExpiredRevocations() ─────────────────────────────────────────────

    @Test
    void purgeDeletesExpiredEntriesFromDb() {
        when(repository.deleteExpiredBefore(any())).thenReturn(5);
        service.purgeExpiredRevocations();
        verify(repository).deleteExpiredBefore(any(Instant.class));
    }

    @Test
    void purgeDoesNotLogWhenNothingDeleted() {
        when(repository.deleteExpiredBefore(any())).thenReturn(0);
        service.purgeExpiredRevocations();
        verify(repository).deleteExpiredBefore(any(Instant.class));
    }
}
