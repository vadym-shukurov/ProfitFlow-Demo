package com.profitflow.application.exception;

/**
 * User-correctable input violation from the application layer (use cases / services).
 * Mapped to HTTP 400 by {@link com.profitflow.adapter.in.web.ApiExceptionHandler}.
 *
 * <p>Prefer this over raw {@link IllegalArgumentException} for API boundaries so
 * internal libraries throwing {@code IllegalArgumentException} are not accidentally
 * exposed with unsafe messages.
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
