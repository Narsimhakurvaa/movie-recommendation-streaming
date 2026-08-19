package com.cinevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an upstream metadata provider fails or times out.
 *
 * <p>Renders as 502 so callers can distinguish "our dependency is unhealthy"
 * from "your request was wrong". The upstream body is never propagated.
 */
public class ExternalProviderException extends ApplicationException {

    public ExternalProviderException(String message) {
        super(HttpStatus.BAD_GATEWAY, "EXTERNAL_PROVIDER_ERROR", message);
    }

    public ExternalProviderException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "EXTERNAL_PROVIDER_ERROR", message, cause);
    }
}
