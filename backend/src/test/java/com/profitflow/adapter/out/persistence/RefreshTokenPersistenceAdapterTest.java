package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.RefreshTokenEntity;
import com.profitflow.adapter.out.persistence.jpa.RefreshTokenRepository;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort.RefreshTokenView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenPersistenceAdapter}.
 *
 * <p>Verifies the translation between {@link RefreshTokenView} projections
 * and {@link RefreshTokenEntity} JPA entities, keeping the security layer
 * decoupled from JPA types.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenPersistenceAdapterTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RefreshTokenPersistenceAdapter(repository);
    }

    // ── save() ────────────────────────────────────────────────────────────────

    @Test
    void savePersistsEntityWithCorrectFields() {
        UUID    id        = UUID.randomUUID();
        String  hash      = "abc123";
        String  username  = "alice";
        Instant issuedAt  = Instant.now();
        Instant expiresAt = issuedAt.plus(7, ChronoUnit.DAYS);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.save(id, hash, username, issuedAt, expiresAt);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(repository).save(captor.capture());
        RefreshTokenEntity saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getTokenHash()).isEqualTo(hash);
        assertThat(saved.getUsername()).isEqualTo(username);
        assertThat(saved.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(saved.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(saved.isRevoked()).isFalse();
    }

    // ── findActiveByHash() ────────────────────────────────────────────────────

    @Test
    void findActiveByHashReturnsViewWhenFound() {
        UUID    id        = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        var     entity    = new RefreshTokenEntity(id, "hash-xyz", "bob", Instant.now(), expiresAt);

        when(repository.findActiveByTokenHash("hash-xyz")).thenReturn(Optional.of(entity));

        Optional<RefreshTokenView> result = adapter.findActiveByHash("hash-xyz");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
        assertThat(result.get().username()).isEqualTo("bob");
        assertThat(result.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void findActiveByHashReturnsEmptyWhenNotFound() {
        when(repository.findActiveByTokenHash("unknown")).thenReturn(Optional.empty());

        assertThat(adapter.findActiveByHash("unknown")).isEmpty();
    }

    // ── revoke() ──────────────────────────────────────────────────────────────

    @Test
    void revokeSetsRevokedFlagAndReason() {
        UUID    id     = UUID.randomUUID();
        var     entity = new RefreshTokenEntity(id, "h", "alice",
                Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS));

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.revoke(id, "ROTATION");

        assertThat(entity.isRevoked()).isTrue();
        verify(repository).save(entity);
    }

    @Test
    void revokeIsNoOpWhenTokenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        // Must not throw; missing token is silently ignored
        adapter.revoke(id, "ROTATION");
    }

    // ── revokeAllForUser() ────────────────────────────────────────────────────

    @Test
    void revokeAllForUserDelegatesToRepository() {
        when(repository.revokeAllForUser(any(), any(), any())).thenReturn(2);

        int count = adapter.revokeAllForUser("alice", "LOGOUT");

        assertThat(count).isEqualTo(2);
        verify(repository).revokeAllForUser(any(), any(), any());
    }

    // ── deleteExpiredBefore() ─────────────────────────────────────────────────

    @Test
    void deleteExpiredBeforeDelegatesToRepository() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        when(repository.deleteExpiredBefore(cutoff)).thenReturn(5);

        int count = adapter.deleteExpiredBefore(cutoff);

        assertThat(count).isEqualTo(5);
        verify(repository).deleteExpiredBefore(cutoff);
    }
}
