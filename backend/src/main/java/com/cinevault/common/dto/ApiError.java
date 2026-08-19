package com.cinevault.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error shape returned by every endpoint.
 *
 * <p>Deliberately excludes stack traces, exception class names and SQL detail:
 * those go to the logs with a correlation id, and the client receives only the
 * id. That keeps troubleshooting possible without leaking internals to callers.
 *
 * @param timestamp        when the failure occurred
 * @param status           HTTP status code
 * @param error            HTTP reason phrase
 * @param code             stable machine-readable code for client branching
 * @param message          safe, human-readable summary
 * @param path             request path that failed
 * @param correlationId    id also written to the server log for this failure
 * @param validationErrors per-field messages, present only for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Standard error response")
public record ApiError(
        @Schema(example = "2026-08-18T10:15:30Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "VALIDATION_FAILED") String code,
        @Schema(example = "Request validation failed") String message,
        @Schema(example = "/api/movies") String path,
        @Schema(example = "b7f1c2d3") String correlationId,
        List<FieldViolation> validationErrors) {

    /**
     * A single field-level validation failure.
     *
     * @param field   the offending property, e.g. {@code email}
     * @param message why it was rejected
     */
    @Schema(name = "FieldViolation")
    public record FieldViolation(
            @Schema(example = "email") String field,
            @Schema(example = "must be a well-formed email address") String message) {
    }

    public static ApiError of(int status, String error, String code, String message,
                              String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, code, message, path, correlationId, null);
    }

    public static ApiError validation(String message, String path, String correlationId,
                                      List<FieldViolation> violations) {
        return new ApiError(Instant.now(), 400, "Bad Request", "VALIDATION_FAILED",
                message, path, correlationId, violations);
    }

    /** Convenience for tests and clients that want the violations as a map. */
    public Map<String, String> asFieldMap() {
        if (validationErrors == null) {
            return Map.of();
        }
        return validationErrors.stream().collect(java.util.stream.Collectors.toMap(
                FieldViolation::field, FieldViolation::message, (a, b) -> a + "; " + b));
    }
}
