package com.profitflow.security;

import com.profitflow.application.port.out.BusinessMetricsPort;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort.RefreshTokenView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenService}.
 *
 * <p>Verifies token issuance, rotation (including expiry detection), revocation,
 * and the SHA-256 hashing invariant.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepositoryPort repository;

    @Mock
    private BusinessMetricsPort metrics;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        final ObjectProvider<RefreshTokenService>[] holder = new ObjectProvider[1];
        holder[0] = new ObjectProvider<>() {
            @Override
            public RefreshTokenService getObject() {
                return service;
            }
        };
        service = new RefreshTokenService(repository, metrics, holder[0]);
    }

    // ── issue() ───────────────────────────────────────────────────────────────

    @Test
    void issueReturnsNonNullRawToken() {
        doNothing().when(repository).save(any(), anyString(), anyString(), any(), any());

        String rawToken = service.issue("alice");

        assertThat(rawToken).isNotNull().isNotBlank();
    }

    @Test
    void issueStoresHashNotRawToken() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(repository).save(any(), hashCaptor.capture(), anyString(), any(), any());

        String rawToken = service.issue("alice");

        // Stored value must not equal the raw token (it is the SHA-256 hash)
        String storedHash = hashCaptor.getValue();
        assertThat(storedHash).isNotEqualTo(rawToken);
        // And the hash must match the expected SHA-256 of the raw token
        assertThat(storedHash).isEqualTo(RefreshTokenService.sha256Hex(rawToken));
    }

    @Test
    void issueStoresCorrectUsername() {
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(repository).save(any(), anyString(), usernameCaptor.capture(), any(), any());

        service.issue("bob");

        assertThat(usernameCaptor.getValue()).isEqualTo("bob");
    }

    // ── rotate() — success ────────────────────────────────────────────────────

    @Test
    void rotateValidTokenReturnsSuccessResult() {
        String rawToken = "valid-raw-token";
        String hash     = RefreshTokenService.sha256Hex(rawToken);
        var view = new RefreshTokenView(UUID.randomUUID(), "alice",
                Instant.now().plus(7, ChronoUnit.DAYS));

        when(repository.findActiveByHash(hash)).thenReturn(Optional.of(view));
        doNothing().when(repository).revoke(any(), anyString());
        doNothing().when(repository).save(any(), anyString(), anyString(), any(), any());

        RefreshTokenService.RotationResult result = service.rotate(rawToken);

        assertThat(result.valid()).isTrue();
        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.newRawToken()).isNotBlank();
        assertThat(result.error()).isNull();
    }

    @Test
    void rotateRevokesOldTokenWithRotationReason() {
        String rawToken = "my-token";
        UUID tokenId    = UUID.randomUUID();
        var view = new RefreshTokenView(tokenId, "alice",
                Instant.now().plus(1, ChronoUnit.DAYS));

        when(repository.findActiveByHash(anyString())).thenReturn(Optional.of(view));
        doNothing().when(repository).revoke(any(), anyString());
        doNothing().when(repository).save(any(), anyString(), anyString(), any(), any());

        service.rotate(rawToken);

        verify(repository).revoke(eq(tokenId), eq("ROTATION"));
    }

    @Test
    void rotateIssuesNewToken() {
        String rawToken = "token-to-rotate";
        var view = new RefreshTokenView(UUID.randomUUID(), "alice",
                Instant.now().plus(1, ChronoUnit.DAYS));

        when(repository.findActiveByHash(anyString())).thenReturn(Optional.of(view));
        doNothing().when(repository).revoke(any(), anyString());
        doNothing().when(repository).save(any(), anyString(), anyString(), any(), any());

        RefreshTokenService.RotationResult result = service.rotate(rawToken);

        // New token should be different from the old one
        assertThat(result.newRawToken()).isNotEqualTo(rawToken);
        // And the service should have called save() twice (once for old token-hash lookup, once for new)
        verify(repository).save(any(), anyString(), eq("alice"), any(), any());
    }

    // ── rotate() — failure paths ──────────────────────────────────────────────

    @Test
    void rotateUnknownTokenReturnsInvalidResult() {
        when(repository.findActiveByHash(anyString())).thenReturn(Optional.empty());
        when(repository.findUsernameByHashAndRevokedReason(anyString(), eq("ROTATION")))
                .thenReturn(Optional.empty());

        RefreshTokenService.RotationResult result = service.rotate("unknown-token");

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).isNotBlank();
        verify(repository, never()).revoke(any(), anyString());
        verify(repository, never()).revokeAllForUser(anyString(), anyString());
    }

    @Test
    void rotateReuseOfRotatedTokenRevokesAllSessionsForUser() {
        when(repository.findActiveByHash(anyString())).thenReturn(Optional.empty());
        when(repository.findUsernameByHashAndRevokedReason(anyString(), eq("ROTATION")))
                .thenReturn(Optional.of("victim"));
        when(repository.revokeAllForUser("victim", "REFRESH_REUSE")).thenReturn(2);

        RefreshTokenService.RotationResult result = service.rotate("old-token");

        assertThat(result.valid()).isFalse();
        verify(metrics).recordRefreshTokenReuseDetected();
        verify(repository).revokeAllForUser("victim", "REFRESH_REUSE");
    }

    @Test
    void rotateExpiredTokenRevokesItAndReturnsInvalid() {
        String rawToken = "expired-token";
        UUID tokenId    = UUID.randomUUID();
        var expiredView = new RefreshTokenView(tokenId, "alice",
                Instant.now().minus(1, ChronoUnit.DAYS)); // already past expiry

        when(repository.findActiveByHash(anyString())).thenReturn(Optional.of(expiredView));
        doNothing().when(repository).revoke(any(), anyString());

        RefreshTokenService.RotationResult result = service.rotate(rawToken);

        assertThat(result.valid()).isFalse();
        verify(repository).revoke(eq(tokenId), eq("EXPIRED"));
    }

    // ── revokeAll() ───────────────────────────────────────────────────────────

    @Test
    void revokeAllDelegatesWithLogoutReason() {
        when(repository.revokeAllForUser("alice", "LOGOUT_ALL")).thenReturn(3);

        int count = service.revokeAll("alice");

        assertThat(count).isEqualTo(3);
        verify(repository).revokeAllForUser("alice", "LOGOUT_ALL");
    }

    // ── sha256Hex() ───────────────────────────────────────────────────────────

    @Test
    void sha256HexProducesDeterministicHash() {
        String h1 = RefreshTokenService.sha256Hex("hello");
        String h2 = RefreshTokenService.sha256Hex("hello");

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void sha256HexProduces64CharHexString() {
        String hash = RefreshTokenService.sha256Hex("any-input");

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void sha256HexDifferentInputsDifferentHashes() {
        assertThat(RefreshTokenService.sha256Hex("a"))
                .isNotEqualTo(RefreshTokenService.sha256Hex("b"));
    }
}
