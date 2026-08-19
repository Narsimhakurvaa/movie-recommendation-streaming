package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/** Raised when a requested entity does not exist. Renders as HTTP 404. */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    /** e.g. {@code new ResourceNotFoundException("Movie", 42)}. */
    public ResourceNotFoundException(String resource, Object identifier) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "%s not found: %s".formatted(resource, identifier));
    }
}
