package com.profitflow.adapter.in.web.dto;

/**
 * Standard error envelope returned for all non-2xx responses.
 *
 * <p>The {@code correlationId} field allows clients to reference a specific
 * request in a support ticket, enabling exact log lookup without exposing
 * internal system details.
 */
public record ApiErrorResponse(String message, String correlationId) {

    /** Convenience constructor for callers that do not have a correlation ID. */
    public ApiErrorResponse(String message) {
        this(message, null);
    }
}
