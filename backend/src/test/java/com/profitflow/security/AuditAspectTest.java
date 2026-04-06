package com.profitflow.security;

import com.profitflow.application.audit.AuditedOperation;
import com.profitflow.adapter.out.persistence.entity.AuditLogEntity;
import com.profitflow.adapter.out.persistence.jpa.AuditLogEntityRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditAspect}.
 *
 * <p>Uses real {@link AuditService} with mocked repository and IP resolver
 * (both interfaces — safely mockable on all JVM versions) instead of mocking
 * {@link AuditService} directly, which fails on Java 25 due to Byte Buddy
 * limitations with concrete classes.
 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    // AuditLogEntityRepository and ClientIpResolverPort are interfaces → safe to mock
    @Mock private AuditLogEntityRepository auditLogRepo;
    @Mock private ClientIpResolverPort     ipResolver;

    // ProceedingJoinPoint is an AspectJ interface → safe to mock
    @Mock private ProceedingJoinPoint pjp;

    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        when(ipResolver.resolveFromContext()).thenReturn(null);
        when(auditLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AuditService realAuditService = new AuditService(auditLogRepo, ipResolver);
        aspect = new AuditAspect(realAuditService);
    }

    /** Builds an inline {@link AuditedOperation} annotation proxy. */
    private AuditedOperation annotation(String action, String entityType,
                                        String spel, boolean critical) {
        return new AuditedOperation() {
            public Class<AuditedOperation> annotationType() { return AuditedOperation.class; }
            public String action()      { return action; }
            public String entityType()  { return entityType; }
            public String entityIdSpEL(){ return spel; }
            public boolean critical()   { return critical; }
        };
    }

    // ── Successful method calls ───────────────────────────────────────────────

    @Test
    void successfulCallRecordsOkDetails() throws Throwable {
        when(pjp.proceed()).thenReturn("result-value");
        var audited = annotation("MY_ACTION", "MyEntity", "", false);

        Object result = aspect.around(pjp, audited);

        assertThat(result).isEqualTo("result-value");
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepo).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("MY_ACTION");
        assertThat(captor.getValue().getDetails()).isEqualTo("OK");
    }

    @Test
    void successfulCallReturnsMethodReturnValue() throws Throwable {
        when(pjp.proceed()).thenReturn(42);
        var audited = annotation("ACTION", "", "", false);

        Object result = aspect.around(pjp, audited);

        assertThat(result).isEqualTo(42);
    }

    // ── Failed method calls ───────────────────────────────────────────────────

    @Test
    void failedCallRecordsFailedDetailsAndRethrows() throws Throwable {
        var cause = new IllegalArgumentException("bad input");
        when(pjp.proceed()).thenThrow(cause);
        var audited = annotation("ACTION", "Entity", "", false);

        assertThatThrownBy(() -> aspect.around(pjp, audited))
                .isSameAs(cause);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepo).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isEqualTo("FAILED: IllegalArgumentException");
    }

    // ── Critical mode ─────────────────────────────────────────────────────────

    @Test
    void criticalAnnotationWritesAuditEntry() throws Throwable {
        when(pjp.proceed()).thenReturn(null);
        var audited = annotation("PRIV_OP", "Resource", "", true);

        aspect.around(pjp, audited);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepo).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("PRIV_OP");
        assertThat(captor.getValue().getDetails()).isEqualTo("OK");
    }

    @Test
    void criticalDbFailureThrowsAuditWriteException() throws Throwable {
        when(pjp.proceed()).thenReturn(null);
        when(auditLogRepo.save(any())).thenThrow(new RuntimeException("DB down"));
        var audited = annotation("PRIV_OP", "Resource", "", true);

        assertThatThrownBy(() -> aspect.around(pjp, audited))
                .isInstanceOf(AuditService.AuditWriteException.class);
    }

    @Test
    void criticalFailureRethrows() throws Throwable {
        var cause = new RuntimeException("critical failure");
        when(pjp.proceed()).thenThrow(cause);
        var audited = annotation("PRIV_OP", "Resource", "", true);

        assertThatThrownBy(() -> aspect.around(pjp, audited))
                .isSameAs(cause);
    }

    // ── Property-path entity ID extraction ────────────────────────────────────

    @Test
    void propertyPathExtractsIdFromResult() throws Throwable {
        record DomainEntity(String id, String name) {}
        when(pjp.proceed()).thenReturn(new DomainEntity("entity-uuid", "Test"));
        var audited = annotation("CREATE", "DomainEntity", "id", false);

        aspect.around(pjp, audited);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepo).save(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo("entity-uuid");
    }

    @Test
    void resultPrefixExtractsIdFromResult() throws Throwable {
        record DomainEntity(String id, String name) {}
        when(pjp.proceed()).thenReturn(new DomainEntity("uuid-2", "X"));
        var audited = annotation("CREATE", "DomainEntity", "#result.id", false);

        aspect.around(pjp, audited);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepo).save(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo("uuid-2");
    }

    @Test
    void nullResultYieldsNullEntityId() throws Throwable {
        when(pjp.proceed()).thenReturn(null);
        var audited = annotation("DELETE", "Entity", "id", false);

        aspect.around(pjp, audited);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepo).save(captor.capture());
        assertThat(captor.getValue().getEntityId()).isNull();
    }

    @Test
    void invalidPropertyPathFallsBackToNullEntityId() throws Throwable {
        when(pjp.proceed()).thenReturn("plain-string");
        // BeanWrapper cannot resolve nested property on a String
        var audited = annotation("ACTION", "Entity", "nonExistentProperty", false);

        Object result = aspect.around(pjp, audited);

        assertThat(result).isEqualTo("plain-string");
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepo).save(captor.capture());
        assertThat(captor.getValue().getEntityId()).isNull();
    }
}
