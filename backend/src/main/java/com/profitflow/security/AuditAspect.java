package com.profitflow.security;

import com.profitflow.application.audit.AuditedOperation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that intercepts methods annotated with
 * {@link com.profitflow.application.audit.AuditedOperation} and
 * writes an audit log entry <em>after</em> each call (whether successful or not).
 *
 * <h2>Entity ID extraction</h2>
 * If {@link AuditedOperation#entityIdSpEL()} is set, the expression is evaluated
 * against the method's return value (using {@code #result} as root) and the
 * result is stored as the {@code entityId} in the audit entry.
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

    private static final ExpressionParser SPEL = new SpelExpressionParser();

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
     * Evaluates the SpEL expression against the method result to extract an entity ID.
     *
     * @param spel   the expression, or blank to skip evaluation
     * @param result the method return value used as the SpEL root object
     * @return the extracted entity ID string, or {@code null} if none
     */
    private static String extractEntityId(String spel, Object result) {
        if (spel == null || spel.isBlank() || result == null) {
            return null;
        }
        try {
            // Treat SpEL as potentially unsafe: we support only read-only property paths, and
            // intentionally reject method calls (e.g. `id()`), type references (T(...)),
            // constructors (`new ...`), and other expression features.
            //
            // For compatibility, allow `#result.<path>` and normalize it to `<path>`.
            String normalized = spel.strip();
            if (normalized.startsWith("#result.")) {
                normalized = normalized.substring("#result.".length());
            }

            // Only allow property paths like "id" or "user.id".
            if (!normalized.matches("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")) {
                log.warn("Rejected non-property entityIdSpEL='{}'", spel);
                return null;
            }

            Expression expr  = SPEL.parseExpression(normalized);
            StandardEvaluationContext ctx = new StandardEvaluationContext(result);
            Object value = expr.getValue(ctx);
            return value != null ? value.toString() : null;
        } catch (Exception ex) {
            log.warn("Failed to evaluate entityIdSpEL='{}': {}", spel, ex.getMessage());
            return null;
        }
    }
}
