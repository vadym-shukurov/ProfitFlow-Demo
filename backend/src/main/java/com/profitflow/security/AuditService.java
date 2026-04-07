package com.profitflow.security;

import com.profitflow.adapter.out.persistence.entity.AuditLogEntity;
import com.profitflow.adapter.out.persistence.jpa.AuditLogEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes immutable audit log entries for every state-changing operation.
 *
 * <h2>Critical vs. best-effort audit writes</h2>
 * <ul>
 *   <li>{@link #record} — best-effort; swallows exceptions so a DB failure does
 *       not break the business operation. Use for non-privileged write operations.</li>
 *   <li>{@link #recordCritical} — fail-hard; throws {@link AuditWriteException} if
 *       the audit entry cannot be persisted. Use for privileged operations (admin
 *       actions, allocation runs, bulk imports) where an unrecorded action is a
 *       compliance violation.</li>
 * </ul>
 *
 * <h2>Transaction isolation</h2>
 * Audit writes use {@link Propagation#REQUIRES_NEW} so that a rolled-back business
 * transaction does not roll back the audit entry — a failed attempt is itself
 * a security-relevant event and must be preserved.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogEntityRepository auditLogRepo;
    private final ClientIpResolverPort     clientIpResolver;
    private final ObjectProvider<AuditService> selfProvider;

    public AuditService(AuditLogEntityRepository auditLogRepo,
                        ClientIpResolverPort clientIpResolver,
                        ObjectProvider<AuditService> selfProvider) {
        this.auditLogRepo    = auditLogRepo;
        this.clientIpResolver = clientIpResolver;
        this.selfProvider     = selfProvider;
    }

    /**
     * Records a completed operation — best-effort. Swallows DB exceptions so
     * the business operation is never blocked by an audit failure.
     *
     * @param action     short upper-snake-case identifier, e.g. {@code "ALLOCATION_RUN_EXECUTED"}
     * @param entityType entity class name, or {@code null} for bulk ops
     * @param entityId   primary key of the affected entity, or {@code null}
     * @param details    freeform summary string (max 2000 chars)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(String action, String entityType, String entityId, String details) {
        try {
            persist(action, entityType, entityId, details);
        } catch (Exception ex) {
            log.error("AUDIT_WRITE_FAILED action={} correlationId={}: {}",
                    action, MDC.get(CorrelationIdFilter.MDC_KEY), ex.getMessage());
        }
    }

    @SuppressWarnings("java:S6213")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, String details) {
        // Ensure the REQUIRES_NEW boundary is applied via the Spring proxy (Sonar java:S6809).
        selfProvider.getObject().recordAudit(action, entityType, entityId, details);
    }

    /**
     * Records a privileged operation — fail-hard. Throws if the audit entry
     * cannot be persisted, causing the enclosing transaction to roll back.
     *
     * <p>Use this for operations that <em>must</em> appear in the audit trail
     * as a compliance requirement (e.g. admin role changes, allocation runs,
     * CSV bulk imports, rule replacements).
     *
     * @throws AuditWriteException if the audit entry cannot be written
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCritical(String action, String entityType, String entityId, String details) {
        try {
            persist(action, entityType, entityId, details);
        } catch (Exception ex) {
            String msg = String.format(
                    "CRITICAL_AUDIT_WRITE_FAILED action=%s correlationId=%s: %s",
                    action, MDC.get(CorrelationIdFilter.MDC_KEY), ex.getMessage());
            log.error(msg);
            throw new AuditWriteException(msg, ex);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void persist(String action, String entityType, String entityId, String details) {
        String username      = resolveUsername();
        String ip            = clientIpResolver.resolveFromContext();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        AuditLogEntity entry = new AuditLogEntity(
                UUID.randomUUID(),
                Instant.now(),
                username,
                ip,
                action,
                entityType,
                entityId,
                truncate(details, 2000),
                correlationId);

        auditLogRepo.save(entry);
    }

    private static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }

    private static String truncate(String s, int maxLength) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLength ? s : s.substring(0, maxLength - 3) + "...";
    }

    // ── Exception types ───────────────────────────────────────────────────────

    /** Thrown by {@link #recordCritical} when an audit entry cannot be persisted. */
    public static class AuditWriteException extends RuntimeException {
        public AuditWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
