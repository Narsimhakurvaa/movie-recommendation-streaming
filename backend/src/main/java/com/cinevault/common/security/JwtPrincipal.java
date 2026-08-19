package com.cinevault.common.security;

import java.util.Set;

/**
 * The authenticated caller, as reconstructed from a verified access token.
 *
 * <p>Held as the authentication principal so controllers can obtain the user id
 * without a database lookup on every request.
 *
 * @param userId the authenticated user's identifier
 * @param email  their email address
 * @param roles  granted authorities, prefixed with {@code ROLE_}
 */
public record JwtPrincipal(Long userId, String email, Set<String> roles) {

    public boolean isAdmin() {
        return roles.contains("ROLE_ADMIN");
    }
}
