package com.cinevault.provider;

import java.time.LocalDate;
import java.util.List;

/**
 * Provider-neutral film metadata.
 *
 * <p>Every provider maps its own response shape into this record, so the
 * synchronisation code has exactly one format to understand. Fields absent
 * upstream arrive as {@code null} or empty rather than being invented.
 *
 * @param providerId   identifier in the source system
 * @param title        localised title
 * @param originalTitle title in the original language
 * @param overview     synopsis
 * @param releaseDate  release date, may be {@code null}
 * @param runtimeMinutes runtime, may be {@code null} for summary responses
 * @param originalLanguage ISO-639-1 code
 * @param originCountry primary production country
 * @param posterUrl    fully-qualified poster URL
 * @param backdropUrl  fully-qualified backdrop URL
 * @param trailerUrl   official trailer, typically YouTube
 * @param rating       provider rating on a 0-10 scale
 * @param voteCount    number of votes behind that rating
 * @param popularity   provider popularity index
 * @param adult        adult-content flag
 * @param genres       genre names
 * @param keywords     thematic keywords
 * @param cast         billed cast, in order
 * @param directors    credited directors
 * @param writers      credited writers
 * @param productionCompanies production company names
 */
public record ProviderMovie(
        String providerId,
        String title,
        String originalTitle,
        String overview,
        LocalDate releaseDate,
        Integer runtimeMinutes,
        String originalLanguage,
        String originCountry,
        String posterUrl,
        String backdropUrl,
        String trailerUrl,
        double rating,
        int voteCount,
        double popularity,
        boolean adult,
        List<String> genres,
        List<String> keywords,
        List<String> cast,
        List<String> directors,
        List<String> writers,
        List<String> productionCompanies) {

    public ProviderMovie {
        genres = genres == null ? List.of() : List.copyOf(genres);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        cast = cast == null ? List.of() : List.copyOf(cast);
        directors = directors == null ? List.of() : List.copyOf(directors);
        writers = writers == null ? List.of() : List.copyOf(writers);
        productionCompanies = productionCompanies == null
                ? List.of() : List.copyOf(productionCompanies);
    }
}
