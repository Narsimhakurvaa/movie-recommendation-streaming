package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/** Raised when a client exceeds its request budget. Renders as HTTP 429. */
public class RateLimitExceededException extends ApplicationException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** Value advertised to the client in the {@code Retry-After} header. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
