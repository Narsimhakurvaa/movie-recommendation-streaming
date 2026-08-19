package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/** Raised for semantically invalid input that bean validation cannot express. */
public class BadRequestException extends ApplicationException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
