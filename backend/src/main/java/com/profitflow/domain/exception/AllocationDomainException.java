package com.profitflow.domain.exception;

/**
 * Thrown when the ABC allocation engine encounters a domain-rule violation —
 * for example, a missing activity-to-product mapping or a zero driver-weight sum.
 *
 * <p>Using a dedicated type (rather than raw {@link IllegalArgumentException}) lets
 * adapter layers translate it cleanly to HTTP 422 or a finance-specific error code
 * without coupling the domain to web concerns.
 */
public class AllocationDomainException extends RuntimeException {

    public AllocationDomainException(String message) {
        super(message);
    }

    public AllocationDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
