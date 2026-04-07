package com.profitflow.security;

import com.profitflow.adapter.out.persistence.entity.AuditLogEntity;
import com.profitflow.adapter.out.persistence.jpa.AuditLogEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditService}.
 *
 * <p>Covers best-effort {@link AuditService#record} (swallows exceptions) and
 * fail-hard {@link AuditService#recordCritical} (throws on persistence failure).
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogEntityRepository repo;
    @Mock private ClientIpResolverPort     ipResolver;

    private AuditService service;

    @BeforeEach
    void setUp() {
        final ObjectProvider<AuditService>[] holder = new ObjectProvider[1];
        holder[0] = new ObjectProvider<>() {
            @Override
            public AuditService getObject() {
                return service;
            }
        };
        service = new AuditService(repo, ipResolver, holder[0]);
        // Default: no active request context → IP resolver returns null
        when(ipResolver.resolveFromContext()).thenReturn(null);
        // Clear security context between tests
        SecurityContextHolder.clearContext();
    }

    // ── record() — best-effort ────────────────────────────────────────────────

    @Test
    void recordPersistsAuditEntry() {
        service.record("COST_CREATED", "ResourceCost", "rc-123", "label=IT");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repo).save(captor.capture());
        AuditLogEntity entry = captor.getValue();

        assertThat(entry.getAction()).isEqualTo("COST_CREATED");
        assertThat(entry.getEntityType()).isEqualTo("ResourceCost");
        assertThat(entry.getEntityId()).isEqualTo("rc-123");
        assertThat(entry.getDetails()).isEqualTo("label=IT");
    }

    @Test
    void recordUsesAnonymousWhenNoSecurityContext() {
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        service.record("ACTION", "Entity", null, "details");
        verify(repo).save(captor.capture());

        assertThat(captor.getValue().getUsername()).isEqualTo("ANONYMOUS");
    }

    @Test
    void recordUsesAuthenticatedUsername() {
        // Use a concrete Authentication implementation — Java 25 cannot mock Authentication
        var auth = new UsernamePasswordAuthenticationToken(
                "alice", null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        SecurityContextHolder.setContext(new SecurityContextImpl(auth));

        service.record("ACTION", "Entity", null, "details");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    void recordSwallowsDbExceptionWithoutThrowing() {
        when(repo.save(any())).thenThrow(new RuntimeException("DB down"));

        // Must not propagate — audit failure must never break the business operation
        service.record("ACTION", "Entity", null, "details");
    }

    @Test
    void recordTruncatesDetailsOver2000Chars() {
        String longDetails = "x".repeat(3000);
        service.record("ACTION", "Entity", null, longDetails);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDetails()).hasSizeLessThanOrEqualTo(2000);
        assertThat(captor.getValue().getDetails()).endsWith("...");
    }

    @Test
    void recordHandlesNullDetails() {
        service.record("ACTION", "Entity", null, null);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isNull();
    }

    // ── recordCritical() — fail-hard ──────────────────────────────────────────

    @Test
    void recordCriticalPersistsEntryOnSuccess() {
        service.recordCritical("ALLOC_RUN", "AllocationRun", "run-1", "OK");
        verify(repo).save(any(AuditLogEntity.class));
    }

    @Test
    void recordCriticalThrowsWhenDbFails() {
        when(repo.save(any())).thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> service.recordCritical("ALLOC_RUN", "AllocationRun", null, "OK"))
                .isInstanceOf(AuditService.AuditWriteException.class)
                .hasMessageContaining("CRITICAL_AUDIT_WRITE_FAILED");
    }

    @Test
    void recordCriticalNeverSwallowsException() {
        when(repo.save(any())).thenThrow(new IllegalStateException("serialization error"));

        assertThatThrownBy(() -> service.recordCritical("PRIVILEGED_OP", null, null, ""))
                .isInstanceOf(AuditService.AuditWriteException.class);
    }

    // ── IP resolution ─────────────────────────────────────────────────────────

    @Test
    void recordStoresIpFromResolver() {
        when(ipResolver.resolveFromContext()).thenReturn("10.0.0.5");

        service.record("ACTION", "Entity", null, "details");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("10.0.0.5");
    }

    @Test
    void recordHandlesNullIpGracefully() {
        when(ipResolver.resolveFromContext()).thenReturn(null);

        service.record("ACTION", "Entity", null, "details");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isNull();
    }
}
