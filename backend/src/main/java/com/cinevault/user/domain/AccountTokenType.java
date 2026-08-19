package com.cinevault.user.domain;

/** Purpose of an {@link AccountToken}; mirrors the DB check constraint. */
public enum AccountTokenType {
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
