package com.profitflow.application.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an application service method for automatic audit logging.
 *
 * <p>The driving implementation is {@code com.profitflow.security.AuditAspect} in the
 * infrastructure/security adapter; this annotation lives in the application layer so
 * use cases do not depend on the {@code security} package (hexagonal boundary).
 *
 * <h2>Critical (fail-hard) audit</h2>
 * When {@link #critical()} is {@code true}, a failed audit write rolls back the
 * surrounding transaction.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedOperation {

    /** Upper-snake-case action name, e.g. {@code "RESOURCE_COST_CREATED"}. */
    String action();

    /** Entity type affected, e.g. {@code "ResourceCost"}. */
    String entityType() default "";

    /**
     * Nested JavaBean property path read from the method return value (optional
     * {@code #result.} prefix). Only paths like {@code id} or {@code user.id} are
     * allowed — not arbitrary SpEL.
     */
    String entityIdSpEL() default "";

    /**
     * If {@code true}, audit failure prevents the business operation from completing.
     */
    boolean critical() default false;
}
