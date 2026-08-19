package com.cinevault.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read models for the catalogue.
 *
 * <p>Two shapes are exposed deliberately: {@link MovieSummary} for grids and
 * rails, and {@link MovieDetail} for the single-film page. Serving the detail
 * shape everywhere would multiply payload size on the busiest endpoints for
 * data the card never renders.
 */
public final class MovieDtos {

    private MovieDtos() {
    }

    /** Compact card model used by every listing, search result and rail. */
    @Schema(name = "MovieSummary", description = "Compact movie representation for listings")
    public record MovieSummary(
            Long id,
            String title,
            String slug,
            @Schema(example = "2014") Integer releaseYear,
            String posterUrl,
            @Schema(description = "Provider rating, 0-10", example = "8.4")
            BigDecimal externalRating,
            @Schema(description = "Platform rating from our users, 0-5", example = "4.6")
            BigDecimal averageRating,
            int ratingCount,
            Integer runtimeMinutes,
            List<String> genres,
            @Schema(description = "Whether the signed-in user has saved this title")
            Boolean inWatchlist,
            @Schema(description = "The signed-in user's own score, if they have rated it")
            Integer userRating) {

        /** Returns a copy carrying the signed-in user's personal state. */
        public MovieSummary withUserState(Boolean saved, Integer rating) {
            return new MovieSummary(id, title, slug, releaseYear, posterUrl, externalRating,
                    averageRating, ratingCount, runtimeMinutes, genres, saved, rating);
        }
    }

    /** Full model for the movie detail page. */
    @Schema(name = "MovieDetail", description = "Complete movie representation")
    public record MovieDetail(
            Long id,
            String title,
            String originalTitle,
            String slug,
            String tagline,
            String overview,
            LocalDate releaseDate,
            Integer releaseYear,
            Integer runtimeMinutes,
            String originalLanguage,
            String originCountry,
            String status,
            String posterUrl,
            String backdropUrl,
            @Schema(description = "Official trailer, typically a YouTube URL")
            String trailerUrl,
            String homepageUrl,
            BigDecimal externalRating,
            int externalVoteCount,
            BigDecimal averageRating,
            int ratingCount,
            BigDecimal popularity,
            Long budget,
            Long revenue,
            boolean adult,
            List<String> productionCompanies,
            List<String> genres,
            List<String> keywords,
            List<CreditSummary> cast,
            List<CreditSummary> directors,
            List<CreditSummary> writers,
            Boolean inWatchlist,
            Integer userRating) {
    }

    /** A single cast or crew credit. */
    @Schema(name = "CreditSummary")
    public record CreditSummary(
            Long personId,
            String name,
            @Schema(description = "Role played, for cast credits") String characterName,
            @Schema(description = "Job title, for crew credits") String job,
            String profileUrl) {
    }

    /** Lightweight result for search type-ahead. */
    @Schema(name = "MovieSuggestionResponse")
    public record MovieSuggestionResponse(
            Long id,
            String title,
            String slug,
            String posterUrl,
            Integer releaseYear) {
    }

    /** A genre with how many films carry it. */
    @Schema(name = "GenreResponse")
    public record GenreResponse(
            Long id,
            String name,
            String slug,
            @Schema(description = "Number of films in this genre") long movieCount) {
    }
}
