package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.RefreshTokenEntity;
import com.profitflow.adapter.out.persistence.jpa.RefreshTokenRepository;
import com.profitflow.application.port.out.RefreshTokenRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter that implements {@link RefreshTokenRepositoryPort} using JPA.
 *
 * <p>This adapter translates between the domain-neutral
 * {@link RefreshTokenRepositoryPort.RefreshTokenView} projection and the
 * {@link RefreshTokenEntity} JPA entity, keeping the security layer free of
 * any JPA/Hibernate dependencies.
 */
@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepositoryPort {

    private final RefreshTokenRepository repository;

    public RefreshTokenPersistenceAdapter(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(UUID id, String tokenHash, String username,
                     Instant issuedAt, Instant expiresAt) {
        repository.save(new RefreshTokenEntity(id, tokenHash, username, issuedAt, expiresAt));
    }

    @Override
    public Optional<RefreshTokenView> findActiveByHash(String tokenHash) {
        return repository.findActiveByTokenHash(tokenHash)
                .map(e -> new RefreshTokenView(e.getId(), e.getUsername(), e.getExpiresAt()));
    }

    @Override
    public Optional<String> findUsernameByHashAndRevokedReason(String tokenHash, String reason) {
        return repository.findUsernameByHashAndRevokedReason(tokenHash, reason);
    }

    @Override
    public void revoke(UUID id, String reason) {
        repository.findById(id).ifPresent(e -> {
            e.revoke(reason);
            repository.save(e);
        });
    }

    @Override
    public int revokeByHash(String tokenHash, String reason) {
        return repository.revokeByHash(tokenHash, Instant.now(), reason);
    }

    @Override
    public int revokeAllForUser(String username, String reason) {
        return repository.revokeAllForUser(username, Instant.now(), reason);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return repository.deleteExpiredBefore(cutoff);
    }
}
