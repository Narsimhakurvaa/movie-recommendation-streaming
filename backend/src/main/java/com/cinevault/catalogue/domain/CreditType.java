package com.cinevault.catalogue.domain;

/** Role a person held on a film; mirrors the DB check constraint. */
public enum CreditType {
    CAST,
    DIRECTOR,
    WRITER,
    PRODUCER
}
