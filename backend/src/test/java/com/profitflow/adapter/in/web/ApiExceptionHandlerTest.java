package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.ApiErrorResponse;
import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.exception.ResourceConflictException;
import com.profitflow.application.exception.ResourceNotFoundException;
import com.profitflow.domain.exception.AllocationDomainException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApiExceptionHandler}.
 *
 * <p>Directly instantiates the advice class to keep tests fast and independent
 * of the Spring MVC stack. Each test verifies the correct HTTP status code and
 * that no sensitive information leaks through the response body.
 */
class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
    }

    @Test
    void domainErrorReturns422WithUserFacingMessage() {
        var ex = new AllocationDomainException("Circular allocation rule detected");
        ResponseEntity<ApiErrorResponse> response = handler.domainError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Circular allocation rule detected");
        assertThat(response.getBody().correlationId()).isEqualTo("n/a"); // no MDC in unit test
    }

    @Test
    void invalidInputReturns400() {
        var ex = new InvalidInputException("CSV too large");
        ResponseEntity<ApiErrorResponse> response = handler.invalidInput(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("CSV too large");
    }

    @Test
    void notFoundReturns404() {
        var ex = new ResourceNotFoundException("Allocation run not found: 7");
        ResponseEntity<ApiErrorResponse> response = handler.notFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Allocation run not found: 7");
    }

    @Test
    void resourceConflictReturns409() {
        var ex = new ResourceConflictException("duplicate code");
        ResponseEntity<ApiErrorResponse> response = handler.resourceConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("duplicate code");
    }

    @Test
    void illegalArgumentReturns400() {
        var ex = new IllegalArgumentException("Amount must be positive");
        ResponseEntity<ApiErrorResponse> response = handler.badRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Amount must be positive");
    }

    @Test
    void illegalStateReturns409() {
        var ex = new IllegalStateException("Allocation run already in progress");
        ResponseEntity<ApiErrorResponse> response = handler.conflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Allocation run already in progress");
    }

    @Test
    void validationFailureReturns400WithFirstFieldError() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        var ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiErrorResponse> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("name").contains("must not be blank");
    }

    @Test
    void validationFailureWithNoFieldErrorsReturnsGenericMessage() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        // No field errors added — should fall back to generic message
        var ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiErrorResponse> response = handler.validation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Request validation failed");
    }

    @Test
    void constraintViolationReturns400() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> v = mock(ConstraintViolation.class);
        when(v.getMessage()).thenReturn("must be positive");
        var ex = new ConstraintViolationException(Set.of(v));

        ResponseEntity<ApiErrorResponse> response = handler.constraintViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Invalid request parameter");
    }

    @Test
    void unreadableBodyReturns400WithGenericMessage() {
        var ex = new HttpMessageNotReadableException("Unexpected character", (Throwable) null);
        ResponseEntity<ApiErrorResponse> response = handler.unreadableBody(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON request body.");
    }

    @Test
    void missingParameterReturns400() {
        var ex = new MissingServletRequestParameterException("page", "int");
        ResponseEntity<ApiErrorResponse> response = handler.missingParameter(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Missing required parameter: page");
    }

    @Test
    void accessDeniedIsRethrownForSecurityHandling() {
        var ex = new AccessDeniedException("denied");
        assertThatThrownBy(() -> handler.internalError(ex)).isSameAs(ex);
    }

    @Test
    void unexpectedExceptionReturns500WithoutStackTrace() {
        var ex = new RuntimeException("db connection refused; host=db-internal password=secret");
        ResponseEntity<ApiErrorResponse> response = handler.internalError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        // Must NOT expose internal details — only a generic message + correlation ID
        assertThat(response.getBody().message()).doesNotContain("db-internal");
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().message()).startsWith("An unexpected error occurred");
    }

    @Test
    void correlationIdIsIncludedInAllResponses() {
        var ex = new AllocationDomainException("err");
        ResponseEntity<ApiErrorResponse> response = handler.domainError(ex);

        // "n/a" is the default when no MDC correlation ID is set
        assertThat(response.getBody().correlationId()).isNotNull();
    }
}
