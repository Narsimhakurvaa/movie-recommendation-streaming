package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an authenticated principal attempts something they are not
 * entitled to, such as editing another user's review. Renders as HTTP 403.
 */
public class AccessDeniedAppException extends ApplicationException {

    public AccessDeniedAppException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
