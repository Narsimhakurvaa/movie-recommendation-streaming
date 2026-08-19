package com.cinevault.catalogue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Filter and sort parameters for movie discovery.
 *
 * <p>Bound as a single object rather than a dozen loose {@code @RequestParam}
 * arguments: it keeps the controller signature readable, allows the whole set
 * to be validated as a unit, and means adding a filter does not change any
 * method signature.
 *
 * @param query      free-text title fragment
 * @param genres     genre slugs; semantics controlled by {@code matchAllGenres}
 * @param matchAllGenres require every listed genre rather than any of them
 * @param yearFrom   earliest release year, inclusive
 * @param yearTo     latest release year, inclusive
 * @param minRating  minimum provider rating, 0-10
 * @param maxRating  maximum provider rating, 0-10
 * @param languages  ISO-639-1 codes
 * @param minRuntime minimum runtime in minutes
 * @param maxRuntime maximum runtime in minutes
 * @param includeAdult include adult titles; defaults to false
 * @param sort       one of the values in {@link MovieSortOption}
 */
@Schema(name = "MovieSearchCriteria", description = "Discovery filters")
public record MovieSearchCriteria(
        @Size(max = 120) String query,
        List<String> genres,
        Boolean matchAllGenres,
        @Min(1870) @Max(2200) Integer yearFrom,
        @Min(1870) @Max(2200) Integer yearTo,
        @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal minRating,
        @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal maxRating,
        List<String> languages,
        @Min(1) @Max(1000) Integer minRuntime,
        @Min(1) @Max(1000) Integer maxRuntime,
        Boolean includeAdult,
        String sort) {

    /** Normalises absent values so the service never deals with nulls. */
    public MovieSearchCriteria {
        genres = genres == null ? List.of() : genres.stream().filter(g -> g != null && !g.isBlank()).toList();
        languages = languages == null ? List.of() : languages.stream().filter(l -> l != null && !l.isBlank()).toList();
        matchAllGenres = matchAllGenres != null && matchAllGenres;
        includeAdult = includeAdult != null && includeAdult;
    }

    public MovieSortOption sortOption() {
        return MovieSortOption.fromKey(sort);
    }

    /** True when no filter is set, which lets the service use a cached path. */
    public boolean isUnfiltered() {
        return (query == null || query.isBlank())
                && genres.isEmpty() && languages.isEmpty()
                && yearFrom == null && yearTo == null
                && minRating == null && maxRating == null
                && minRuntime == null && maxRuntime == null;
    }
}
