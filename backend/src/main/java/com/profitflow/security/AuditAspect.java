package com.profitflow.security;

import com.profitflow.application.audit.AuditedOperation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that intercepts methods annotated with
 * {@link com.profitflow.application.audit.AuditedOperation} and
 * writes an audit log entry <em>after</em> each call (whether successful or not).
 *
 * <h2>Entity ID extraction</h2>
 * If {@link AuditedOperation#entityIdSpEL()} is set, a <em>property path</em> is read
 * from the method's return value (optional {@code #result.} prefix) and the value is
 * stored as the {@code entityId} in the audit entry. Only simple paths like {@code id}
 * or {@code user.id} are allowed — no SpEL or method calls.
 *
 * <h2>Critical audit mode</h2>
 * When {@link AuditedOperation#critical()} is {@code true}, the aspect delegates
 * to {@link AuditService#recordCritical}, which throws if the audit entry cannot
 * be persisted — causing the enclosing transaction to roll back.
 *
 * <h2>Aspect ordering</h2>
 * {@code @Order(Ordered.LOWEST_PRECEDENCE)} ensures this aspect runs <em>inside</em>
 * the {@code @Transactional} advisor (which uses {@code Integer.MAX_VALUE - 1}).
 * This is critical for the fail-hard guarantee: if {@code recordCritical} throws,
 * the surrounding business transaction is still active and can be rolled back.
 * Without explicit ordering, a Spring version change could cause the audit to run
 * <em>after</em> the transaction commits — making the guarantee hollow.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, AuditedOperation audited) throws Throwable {
        Throwable error  = null;
        Object    result = null;

        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            error = t;
        }

        String details  = error != null ? "FAILED: " + error.getClass().getSimpleName() : "OK";
        String entityId = extractEntityId(audited.entityIdSpEL(), result);

        if (audited.critical()) {
            auditService.recordCritical(audited.action(), audited.entityType(), entityId, details);
        } else {
            auditService.record(audited.action(), audited.entityType(), entityId, details);
        }

        if (error != null) {
            throw error;
        }
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Reads a bean property path from the method result to extract an entity ID.
     * Uses {@link BeanWrapper} instead of SpEL so the path cannot be evaluated as code.
     *
     * @param spel   the path (legacy name: was SpEL-shaped); blank skips
     * @param result the method return value used as the root bean
     * @return the extracted entity ID string, or {@code null} if none
     */
    private static String extractEntityId(String spel, Object result) {
        if (spel == null || spel.isBlank() || result == null) {
            return null;
        }
        try {
            // For compatibility, allow `#result.<path>` and normalize it to `<path>`.
            String normalized = spel.strip();
            if (normalized.startsWith("#result.")) {
                normalized = normalized.substring("#result.".length());
            }

            // Only allow nested property paths like "id" or "user.id" (no SpEL, methods, etc.).
            // Validate by segments — avoids java:S5998 (nested * quantifiers in one big regex).
            if (!isAllowedBeanPropertyPath(normalized)) {
                log.warn("Rejected non-property entityIdSpEL='{}'", spel);
                return null;
            }

            BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(result);
            Object value = wrapper.getPropertyValue(normalized);
            return value != null ? value.toString() : null;
        } catch (Exception ex) {
            log.warn("Failed to evaluate entityIdSpEL='{}': {}", spel, ex.getMessage());
            return null;
        }
    }

    /** JavaBean-style segment: {@code [A-Za-z_][A-Za-z0-9_]*}; path is dot-separated segments. */
    private static boolean isAllowedBeanPropertyPath(String path) {
        if (path.isEmpty()) {
            return false;
        }
        for (String segment : path.split("\\.", -1)) {
            if (segment.isEmpty() || !segment.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
                return false;
            }
        }
        return true;
    }
}
