package com.profitflow.application.exception;

/**
 * Raised when a client references a resource that does not exist (HTTP 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
