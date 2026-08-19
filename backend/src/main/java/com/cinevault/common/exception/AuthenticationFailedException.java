package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when credentials or a token are rejected.
 *
 * <p>The message is intentionally coarse ("Invalid email or password") so the
 * response cannot be used to enumerate which accounts exist.
 */
public class AuthenticationFailedException extends ApplicationException {

    public AuthenticationFailedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", message);
    }
}
