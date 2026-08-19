package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when creating something that already exists, such as registering an
 * email already in use or adding a duplicate watchlist entry. Renders as 409.
 */
public class DuplicateResourceException extends ApplicationException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "RESOURCE_ALREADY_EXISTS", message);
    }
}
