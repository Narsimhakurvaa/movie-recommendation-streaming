package com.cinevault.catalogue.repository;

/** Genre together with how many films carry it. */
public record GenreCount(Long id, String name, String slug, long total) {
}
