package com.cinevault.catalogue.dto;

import org.springframework.data.domain.Sort;

import java.util.Locale;

/**
 * The permitted sort orders for movie discovery.
 *
 * <p>An allowlist enum rather than a free-text Spring {@code Sort} parameter.
 * Accepting an arbitrary property name would let a caller sort by any column on
 * the entity - including ones that are not indexed, which is a cheap way to
 * force a full table scan - and would leak the persistence model into the API.
 */
public enum MovieSortOption {

    POPULARITY("popularity", Sort.by(Sort.Direction.DESC, "popularity")),
    RATING("rating", Sort.by(Sort.Direction.DESC, "externalRating")
            .and(Sort.by(Sort.Direction.DESC, "externalVoteCount"))),
    RELEASE_DATE("releaseDate", Sort.by(Sort.Direction.DESC, "releaseDate")),
    OLDEST("oldest", Sort.by(Sort.Direction.ASC, "releaseDate")),
    TITLE("title", Sort.by(Sort.Direction.ASC, "title")),
    RUNTIME("runtime", Sort.by(Sort.Direction.DESC, "runtimeMinutes"));

    private final String key;
    private final Sort sort;

    MovieSortOption(String key, Sort sort) {
        this.key = key;
        this.sort = sort;
    }

    public String key() {
        return key;
    }

    public Sort sort() {
        return sort;
    }

    /** Falls back to popularity for an unknown or absent key. */
    public static MovieSortOption fromKey(String key) {
        if (key == null || key.isBlank()) {
            return POPULARITY;
        }
        String normalised = key.trim().toLowerCase(Locale.ROOT);
        for (MovieSortOption option : values()) {
            if (option.key.toLowerCase(Locale.ROOT).equals(normalised)
                    || option.name().toLowerCase(Locale.ROOT).equals(normalised)) {
                return option;
            }
        }
        return POPULARITY;
    }
}
