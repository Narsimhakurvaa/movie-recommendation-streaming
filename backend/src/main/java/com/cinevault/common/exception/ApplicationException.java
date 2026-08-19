package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for every failure the application raises deliberately.
 *
 * <p>Carrying the HTTP status and a stable error code on the exception itself
 * means {@link GlobalExceptionHandler} needs no growing switch statement: it
 * simply renders whatever the exception declares. Adding a new failure mode is
 * therefore a one-class change.
 */
public abstract class ApplicationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApplicationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApplicationException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    /** Stable, machine-readable code clients may branch on. */
    public String code() {
        return code;
    }
}
