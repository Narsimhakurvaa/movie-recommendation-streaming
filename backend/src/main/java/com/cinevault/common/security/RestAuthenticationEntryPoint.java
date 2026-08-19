package com.cinevault.common.security;

import com.cinevault.common.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Renders 401 responses in the same {@link ApiError} shape as everything else.
 *
 * <p>Without this Spring Security would emit its own HTML/empty response for
 * unauthenticated requests, so clients would need two different error parsers.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // No WWW-Authenticate header: it makes browsers show a native login
        // dialog, which is wrong for a token-based single-page application.
        ApiError body = ApiError.of(401, "Unauthorized", "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource",
                request.getRequestURI(), UUID.randomUUID().toString().substring(0, 8));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
