package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.ApiErrorResponse;
import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.exception.ResourceConflictException;
import com.profitflow.application.exception.ResourceNotFoundException;
import com.profitflow.domain.exception.AllocationDomainException;
import com.profitflow.security.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;

/**
 * Translates domain and validation exceptions to a consistent JSON error envelope.
 *
 * <h2>Security design</h2>
 * <ul>
 *   <li><strong>No stack traces</strong> in responses — internal structure stays private.</li>
 *   <li><strong>Correlation IDs</strong> are included in every error response so clients can
 *       reference a specific request in a support ticket without exposing system internals.</li>
 *   <li>Internal server errors (5xx) return only a generic message — the detailed cause
 *       is logged server-side against the correlation ID.</li>
 *   <li>Domain and validation errors (4xx) return their safe, user-facing messages.</li>
 * </ul>
 *
 * <table border="1">
 *   <caption>Exception → HTTP status mapping</caption>
 *   <tr><th>Exception</th><th>HTTP status</th></tr>
 *   <tr><td>{@link AllocationDomainException}</td><td>422 Unprocessable Entity</td></tr>
 *   <tr><td>{@link InvalidInputException}</td><td>400 Bad Request</td></tr>
 *   <tr><td>{@link ResourceConflictException}</td><td>409 Conflict</td></tr>
 *   <tr><td>{@link ResourceNotFoundException}</td><td>404 Not Found</td></tr>
 *   <tr><td>{@link IllegalArgumentException}</td><td>400 Bad Request (legacy)</td></tr>
 *   <tr><td>{@link IllegalStateException}</td><td>409 Conflict (legacy)</td></tr>
 *   <tr><td>{@link MethodArgumentNotValidException}</td><td>400 Bad Request</td></tr>
 *   <tr><td>{@link ConstraintViolationException}</td><td>400 Bad Request</td></tr>
 *   <tr><td>{@link HttpMessageNotReadableException}</td><td>400 Bad Request</td></tr>
 *   <tr><td>{@link MissingServletRequestParameterException}</td><td>400 Bad Request</td></tr>
 *   <tr><td>{@link Exception} (catch-all)</td><td>500 Internal Server Error</td></tr>
 * </table>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Returns 422 for domain rule violations. The exception message is intentionally user-facing. */
    @ExceptionHandler(AllocationDomainException.class)
    public ResponseEntity<ApiErrorResponse> domainError(AllocationDomainException ex) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(ex.getMessage(), correlationId()));
    }

    /** Returns 400 for explicit application-layer input violations. */
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiErrorResponse> invalidInput(InvalidInputException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(ex.getMessage(), correlationId()));
    }

    /** Returns 409 for application-layer conflict (e.g. stale state). */
    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> resourceConflict(ResourceConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(ex.getMessage(), correlationId()));
    }

    /** Returns 404 when a referenced resource does not exist. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(ex.getMessage(), correlationId()));
    }

    /** Returns 400 for service-layer input guard failures (legacy — prefer {@link InvalidInputException}). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(ex.getMessage(), correlationId()));
    }

    /** Returns 409 for invariant violations (legacy — prefer {@link ResourceConflictException}). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> conflict(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(ex.getMessage(), correlationId()));
    }

    /** Returns 400 for Jakarta Bean Validation failures. Reports the first failing field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> "'" + err.getField() + "': " + err.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(message, correlationId()));
    }

    /** Returns 400 for parameter-level validation failures (e.g. query params, path variables). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> "Invalid request parameter: " + v.getMessage())
                .orElse("Request validation failed");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(message, correlationId()));
    }

    /** Returns 400 for malformed JSON bodies (syntax errors, wrong type). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> unreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Malformed JSON request [correlationId={}]: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("Malformed JSON request body.", correlationId()));
    }

    /** Returns 400 when required query parameters are missing. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> missingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("Missing required parameter: " + ex.getParameterName(), correlationId()));
    }

    /**
     * Catch-all for unexpected exceptions. Logs full details server-side but returns only a
     * generic message to the client, preventing internal information leakage.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> internalError(Exception ex) {
        if (ex instanceof AccessDeniedException ade) {
            throw ade;
        }
        if (ex instanceof AuthenticationException ae) {
            throw ae;
        }
        if (ex instanceof ConstraintViolationException cve) {
            return constraintViolation(cve);
        }
        if (ex instanceof HttpMessageNotReadableException hmnre) {
            return unreadableBody(hmnre);
        }
        if (ex instanceof MissingServletRequestParameterException msrpe) {
            return missingParameter(msrpe);
        }
        String cid = correlationId();
        log.error("Unhandled exception [correlationId={}]: {}", cid, ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "An unexpected error occurred. Reference: " + cid, cid));
    }

    private static String correlationId() {
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        return cid != null ? cid : "n/a";
    }
}
