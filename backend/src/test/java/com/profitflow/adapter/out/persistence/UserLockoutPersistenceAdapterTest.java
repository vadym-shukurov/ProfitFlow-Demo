package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.AppUserEntity;
import com.profitflow.adapter.out.persistence.jpa.AppUserEntityRepository;
import com.profitflow.domain.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLockoutPersistenceAdapterTest {

    @Mock
    private AppUserEntityRepository users;

    private UserLockoutPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserLockoutPersistenceAdapter(users);
    }

    @Test
    void recordFailedLoginUpdatesEntityAndPersists() {
        AppUserEntity u = new AppUserEntity(UUID.randomUUID(), "alice", "a@x.test", "hash", UserRole.ANALYST);
        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.isLocked()).isFalse();

        when(users.findByUsername("alice")).thenReturn(Optional.of(u));

        adapter.recordFailedLogin("alice");

        assertThat(u.getFailedLoginAttempts()).isEqualTo(1);
        verify(users).findByUsername("alice");
        verify(users).save(u);
    }

    @Test
    void recordSuccessfulLoginClearsLockoutAndPersists() {
        AppUserEntity u = new AppUserEntity(UUID.randomUUID(), "bob", "b@x.test", "hash", UserRole.FINANCE_MANAGER);
        for (int i = 0; i < AppUserEntity.MAX_FAILED_ATTEMPTS; i++) {
            u.recordFailedLogin();
        }
        assertThat(u.isLocked()).isTrue();
        Instant lockedUntilBefore = u.getLockedUntil();

        when(users.findByUsername("bob")).thenReturn(Optional.of(u));

        adapter.recordSuccessfulLogin("bob");

        assertThat(u.getFailedLoginAttempts()).isZero();
        assertThat(u.getLockedUntil()).isNull();
        assertThat(lockedUntilBefore).isNotNull();
        verify(users).findByUsername("bob");
        verify(users).save(u);
    }

    @Test
    void missingUserIsNoOp() {
        when(users.findByUsername("missing")).thenReturn(Optional.empty());

        adapter.recordFailedLogin("missing");
        adapter.recordSuccessfulLogin("missing");

        verify(users, times(2)).findByUsername("missing");
        verifyNoMoreInteractions(users);
    }
}

