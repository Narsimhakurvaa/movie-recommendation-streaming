package com.cinevault.recommendation.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

/**
 * An immutable, persistence-free projection of everything the recommendation
 * engine needs to know about a film.
 *
 * <p>The engine deliberately depends on this record rather than on the
 * {@code Movie} JPA entity. That keeps the scoring logic free of Hibernate
 * proxies and lazy-loading concerns, makes it trivially unit-testable without a
 * Spring context, and means the ranking algorithm could later be moved to a
 * separate service without dragging the persistence model along.
 *
 * @param movieId        internal identifier
 * @param title          display title, used when building explanations
 * @param genreIds       genre identifiers this film belongs to
 * @param keywordIds     thematic keyword identifiers
 * @param castIds        identifiers of billed cast members
 * @param directorIds    identifiers of credited directors
 * @param writerIds      identifiers of credited writers
 * @param language       ISO-639-1 original language
 * @param releaseDate    release date, {@code null} when unknown
 * @param externalRating provider rating on a 0-10 scale
 * @param externalVotes  number of provider votes, used for confidence
 * @param platformRating rating from this platform's users on a 0-5 scale
 * @param platformVotes  number of platform ratings
 * @param popularity     provider popularity index
 * @param adult          adult-content flag
 */
public record MovieFeatures(
        long movieId,
        String title,
        Set<Long> genreIds,
        Set<Long> keywordIds,
        Set<Long> castIds,
        Set<Long> directorIds,
        Set<Long> writerIds,
        String language,
        LocalDate releaseDate,
        double externalRating,
        int externalVotes,
        double platformRating,
        int platformVotes,
        double popularity,
        boolean adult) {

    public MovieFeatures {
        genreIds = safeCopy(genreIds);
        keywordIds = safeCopy(keywordIds);
        castIds = safeCopy(castIds);
        directorIds = safeCopy(directorIds);
        writerIds = safeCopy(writerIds);
    }

    private static Set<Long> safeCopy(Set<Long> input) {
        return input == null ? Collections.emptySet() : Set.copyOf(input);
    }

    /** Release year, or {@code null} when the release date is unknown. */
    public Integer releaseYear() {
        return releaseDate == null ? null : releaseDate.getYear();
    }
}
