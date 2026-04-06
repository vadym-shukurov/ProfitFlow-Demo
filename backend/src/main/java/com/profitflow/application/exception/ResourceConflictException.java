package com.profitflow.application.exception;

/**
 * Request conflicts with current server state (duplicate, stale version, etc.).
 * Mapped to HTTP 409 by {@link com.profitflow.adapter.in.web.ApiExceptionHandler}.
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
