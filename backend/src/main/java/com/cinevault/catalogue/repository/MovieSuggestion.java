package com.cinevault.catalogue.repository;

import java.time.LocalDate;

/**
 * Narrow projection for search type-ahead.
 *
 * <p>Returning this instead of {@code Movie} keeps the suggestion query to the
 * five columns the dropdown actually renders, which matters on an endpoint
 * called on every debounced keystroke.
 */
public record MovieSuggestion(
        Long id,
        String title,
        String slug,
        String posterUrl,
        LocalDate releaseDate) {

    public Integer releaseYear() {
        return releaseDate == null ? null : releaseDate.getYear();
    }
}
