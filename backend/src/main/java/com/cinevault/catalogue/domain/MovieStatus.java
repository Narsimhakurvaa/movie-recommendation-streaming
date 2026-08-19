package com.cinevault.catalogue.domain;

/** Release lifecycle of a film; mirrors the DB check constraint. */
public enum MovieStatus {
    RELEASED,
    UPCOMING,
    IN_PRODUCTION,
    CANCELLED
}
