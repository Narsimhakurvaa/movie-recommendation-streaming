package com.cinevault.common.exception;

import com.cinevault.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Translates every exception into the single {@link ApiError} contract.
 *
 * <h2>Information disclosure</h2>
 * <p>Unexpected exceptions are logged in full server-side against a short
 * correlation id, but the client receives only a generic message plus that id.
 * This is the difference between a support engineer being able to find the
 * failure in seconds and an attacker learning the persistence layer's internals
 * from a stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Every failure the application raises on purpose. */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiError> handleApplication(ApplicationException ex, HttpServletRequest request) {
        String correlationId = newCorrelationId();
        // Expected failures are noise at WARN and above; log them at DEBUG
        // unless they indicate a server-side problem.
        if (ex.status().is5xxServerError()) {
            log.error("[{}] {} at {}", correlationId, ex.getMessage(), request.getRequestURI(), ex);
        } else {
            log.debug("[{}] {} at {}", correlationId, ex.getMessage(), request.getRequestURI());
        }

        ApiError body = ApiError.of(ex.status().value(), ex.status().getReasonPhrase(),
                ex.code(), ex.getMessage(), request.getRequestURI(), correlationId);

        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitExceededException rateLimit) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.retryAfterSeconds()));
        }
        return new ResponseEntity<>(body, headers, ex.status());
    }

    /** {@code @Valid} failures on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();
        // Global (class-level) errors have no field; surface them too.
        List<ApiError.FieldViolation> all = new java.util.ArrayList<>(violations);
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                all.add(new ApiError.FieldViolation(error.getObjectName(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage())));

        String correlationId = newCorrelationId();
        log.debug("[{}] validation failed at {}: {}", correlationId, request.getRequestURI(), all);
        return ResponseEntity.badRequest().body(ApiError.validation(
                "Request validation failed", request.getRequestURI(), correlationId, all));
    }

    /** {@code @Validated} failures on path variables and query parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        String correlationId = newCorrelationId();
        log.debug("[{}] constraint violation at {}", correlationId, request.getRequestURI());
        return ResponseEntity.badRequest().body(ApiError.validation(
                "Request validation failed", request.getRequestURI(), correlationId, violations));
    }

    /** Malformed JSON, wrong types in the body, unparseable enums. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        String correlationId = newCorrelationId();
        log.debug("[{}] unreadable request body at {}", correlationId, request.getRequestURI());
        // The parser message can echo payload content, so it is not returned.
        return ResponseEntity.badRequest().body(ApiError.of(400, "Bad Request", "MALFORMED_REQUEST",
                "Request body is missing or malformed", request.getRequestURI(), correlationId));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest request) {
        String correlationId = newCorrelationId();
        return ResponseEntity.badRequest().body(ApiError.validation(
                "Request validation failed", request.getRequestURI(), correlationId,
                List.of(new ApiError.FieldViolation(ex.getParameterName(), "is required"))));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        String correlationId = newCorrelationId();
        String required = ex.getRequiredType() == null ? "the expected type"
                : ex.getRequiredType().getSimpleName();
        return ResponseEntity.badRequest().body(ApiError.validation(
                "Request validation failed", request.getRequestURI(), correlationId,
                List.of(new ApiError.FieldViolation(ex.getName(), "must be " + required))));
    }

    /**
     * A database constraint we did not pre-check, typically a race between two
     * concurrent inserts. The driver message can contain table and column
     * names, so it is logged rather than returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        String correlationId = newCorrelationId();
        log.warn("[{}] data integrity violation at {}", correlationId, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "Conflict",
                "CONSTRAINT_VIOLATION", "The request conflicts with existing data",
                request.getRequestURI(), correlationId));
    }

    /** Spring Security denied an authenticated but unauthorised request. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        String correlationId = newCorrelationId();
        log.debug("[{}] access denied at {}", correlationId, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(403, "Forbidden",
                "ACCESS_DENIED", "You do not have permission to perform this action",
                request.getRequestURI(), correlationId));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest request) {
        String correlationId = newCorrelationId();
        log.debug("[{}] authentication failed at {}", correlationId, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(401, "Unauthorized",
                "AUTHENTICATION_REQUIRED", "Authentication is required to access this resource",
                request.getRequestURI(), correlationId));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of(405,
                "Method Not Allowed", "METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint",
                request.getRequestURI(), newCorrelationId()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(404, "Not Found",
                "ENDPOINT_NOT_FOUND", "No endpoint matches this request",
                request.getRequestURI(), newCorrelationId()));
    }

    /**
     * Final safety net. Anything reaching here is a bug: it is logged at ERROR
     * with the full stack trace, and the caller gets an opaque 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = newCorrelationId();
        log.error("[{}] unhandled exception at {}", correlationId, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(500,
                "Internal Server Error", "INTERNAL_ERROR",
                "An unexpected error occurred. Quote reference " + correlationId + " if reporting this.",
                request.getRequestURI(), correlationId));
    }

    /** Short id: long enough to be unique in logs, short enough to read aloud. */
    private static String newCorrelationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String lastNode(String propertyPath) {
        int index = propertyPath.lastIndexOf('.');
        return index < 0 ? propertyPath : propertyPath.substring(index + 1);
    }
}
