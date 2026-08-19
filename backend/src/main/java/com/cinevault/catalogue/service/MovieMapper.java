package com.cinevault.catalogue.service;

import com.cinevault.catalogue.domain.CreditType;
import com.cinevault.catalogue.domain.Genre;
import com.cinevault.catalogue.domain.Keyword;
import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.domain.MovieCredit;
import com.cinevault.catalogue.dto.MovieDtos.CreditSummary;
import com.cinevault.catalogue.dto.MovieDtos.MovieDetail;
import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Converts catalogue entities into their API representations.
 *
 * <p>Written by hand rather than generated. The mapping is not one-to-one:
 * collections are flattened to name lists, credits are split by type and
 * ordered by billing, and personal state is layered on separately. Expressing
 * that through MapStruct would require enough {@code @Named} helpers that the
 * generated indirection would obscure rather than clarify.
 *
 * <p>Callers must ensure lazy associations are initialised before mapping;
 * {@link #toDetail} is only ever fed by a repository method that fetches them.
 */
@Component
public class MovieMapper {

    /** Cast members shown on the detail page, in billing order. */
    private static final int MAX_CAST = 12;

    /**
     * Maps to the compact listing shape.
     *
     * <p>Genres are read here, so the query that produced the entity must have
     * fetched them; every listing path uses an entity graph that does.
     */
    public MovieSummary toSummary(Movie movie) {
        return new MovieSummary(
                movie.getId(),
                movie.getTitle(),
                movie.getSlug(),
                movie.getReleaseYear(),
                movie.getPosterUrl(),
                movie.getExternalRating(),
                movie.getAverageRating(),
                movie.getRatingCount(),
                movie.getRuntimeMinutes(),
                movie.getGenres().stream().map(Genre::getName).sorted().toList(),
                null,
                null);
    }

    /** Maps to the full detail shape. */
    public MovieDetail toDetail(Movie movie, Boolean inWatchlist, Integer userRating) {
        return new MovieDetail(
                movie.getId(),
                movie.getTitle(),
                movie.getOriginalTitle(),
                movie.getSlug(),
                movie.getTagline(),
                movie.getOverview(),
                movie.getReleaseDate(),
                movie.getReleaseYear(),
                movie.getRuntimeMinutes(),
                movie.getOriginalLanguage(),
                movie.getOriginCountry(),
                movie.getStatus().name(),
                movie.getPosterUrl(),
                movie.getBackdropUrl(),
                movie.getTrailerUrl(),
                movie.getHomepageUrl(),
                movie.getExternalRating(),
                movie.getExternalVoteCount(),
                movie.getAverageRating(),
                movie.getRatingCount(),
                movie.getPopularity(),
                movie.getBudget(),
                movie.getRevenue(),
                movie.isAdult(),
                splitCompanies(movie.getProductionCompanies()),
                movie.getGenres().stream().map(Genre::getName).sorted().toList(),
                movie.getKeywords().stream().map(Keyword::getName).sorted().toList(),
                credits(movie, CreditType.CAST, MAX_CAST),
                credits(movie, CreditType.DIRECTOR, Integer.MAX_VALUE),
                credits(movie, CreditType.WRITER, Integer.MAX_VALUE),
                inWatchlist,
                userRating);
    }

    private List<CreditSummary> credits(Movie movie, CreditType type, int limit) {
        return movie.getCredits().stream()
                .filter(credit -> credit.getCreditType() == type)
                .sorted(Comparator.comparingInt(MovieCredit::getDisplayOrder))
                .limit(limit)
                .map(credit -> new CreditSummary(
                        credit.getPerson().getId(),
                        credit.getPerson().getName(),
                        credit.getCharacterName(),
                        credit.getJob(),
                        credit.getPerson().getProfileUrl()))
                .toList();
    }

    /** Production companies are stored as a delimited string; see the entity. */
    private List<String> splitCompanies(String companies) {
        if (companies == null || companies.isBlank()) {
            return List.of();
        }
        return Arrays.stream(companies.split(","))
                .map(String::trim)
                .filter(company -> !company.isEmpty())
                .toList();
    }
}
