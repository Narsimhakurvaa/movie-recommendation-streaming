package com.cinevault.user.domain;

/**
 * The roles the application understands.
 *
 * <p>Spring Security's {@code hasRole()} prepends {@code ROLE_}, so the stored
 * authority strings carry that prefix while {@link #shortName()} returns the
 * bare form used in annotations.
 */
public enum RoleName {

    ROLE_USER,
    ROLE_ADMIN;

    /** The form used by {@code @PreAuthorize("hasRole('ADMIN')")}. */
    public String shortName() {
        return name().substring("ROLE_".length());
    }
}
