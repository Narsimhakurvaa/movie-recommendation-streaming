package com.cinevault.admin.service;

import com.cinevault.admin.dto.AdminDtos.SyncResult;
import com.cinevault.catalogue.domain.CreditType;
import com.cinevault.catalogue.domain.Genre;
import com.cinevault.catalogue.domain.Keyword;
import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.domain.MovieCredit;
import com.cinevault.catalogue.domain.Person;
import com.cinevault.catalogue.repository.GenreRepository;
import com.cinevault.catalogue.repository.KeywordRepository;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.catalogue.repository.PersonRepository;
import com.cinevault.common.exception.ExternalProviderException;
import com.cinevault.provider.MovieMetadataProvider;
import com.cinevault.provider.ProviderMovie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports films from the configured metadata provider into the local catalogue.
 *
 * <h2>Idempotency</h2>
 * <p>Films are matched on their provider id first, then on slug. Re-running a
 * sync therefore updates existing rows rather than creating duplicates, which
 * matters because this is an operator-triggered action that will be run more
 * than once.
 *
 * <h2>Partial failure</h2>
 * <p>One bad record does not abort the run. Failures are collected as warnings
 * and reported in the result, so an operator can see exactly what was skipped
 * instead of receiving a single opaque error after a half-finished import.
 */
@Service
public class CatalogueSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSyncService.class);

    /** Warnings returned to the operator; the rest are logged only. */
    private static final int MAX_REPORTED_WARNINGS = 20;

    private final MovieMetadataProvider provider;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final KeywordRepository keywordRepository;
    private final PersonRepository personRepository;

    public CatalogueSyncService(MovieMetadataProvider provider,
                                MovieRepository movieRepository,
                                GenreRepository genreRepository,
                                KeywordRepository keywordRepository,
                                PersonRepository personRepository) {
        this.provider = provider;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.keywordRepository = keywordRepository;
        this.personRepository = personRepository;
    }

    /**
     * Runs a synchronisation.
     *
     * @param pages how many provider pages to walk, for popular and trending each
     */
    @Transactional
    public SyncResult synchronise(int pages) {
        if (!provider.isAvailable()) {
            throw new ExternalProviderException(
                    "The configured metadata provider is unavailable");
        }

        List<String> warnings = new ArrayList<>();
        // Deduplicate across the popular and trending feeds, which overlap.
        Map<String, ProviderMovie> fetched = new LinkedHashMap<>();

        for (int page = 1; page <= pages; page++) {
            collect(fetched, warnings, page, true);
            collect(fetched, warnings, page, false);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (ProviderMovie candidate : fetched.values()) {
            try {
                if (candidate.title() == null || candidate.title().isBlank()) {
                    skipped++;
                    continue;
                }
                if (upsert(candidate)) {
                    created++;
                } else {
                    updated++;
                }
            } catch (RuntimeException failure) {
                skipped++;
                String warning = "Skipped '%s': %s"
                        .formatted(candidate.title(), failure.getMessage());
                log.warn(warning, failure);
                if (warnings.size() < MAX_REPORTED_WARNINGS) {
                    warnings.add(warning);
                }
            }
        }

        log.info("Catalogue sync from {} complete: {} fetched, {} created, {} updated, {} skipped",
                provider.name(), fetched.size(), created, updated, skipped);
        return new SyncResult(provider.name(), fetched.size(), created, updated, skipped,
                warnings, Instant.now());
    }

    private void collect(Map<String, ProviderMovie> target, List<String> warnings,
                         int page, boolean popular) {
        try {
            var results = popular ? provider.fetchPopular(page) : provider.fetchTrending(page);
            results.forEach(movie -> target.putIfAbsent(movie.providerId(), movie));
        } catch (ExternalProviderException failure) {
            String warning = "Failed to fetch %s page %d: %s"
                    .formatted(popular ? "popular" : "trending", page, failure.getMessage());
            log.warn(warning);
            if (warnings.size() < MAX_REPORTED_WARNINGS) {
                warnings.add(warning);
            }
        }
    }

    /**
     * Inserts or updates one film.
     *
     * @return {@code true} when a new row was created
     */
    private boolean upsert(ProviderMovie source) {
        Integer providerId = parseProviderId(source.providerId());
        String slug = slugify(source.title(), source.releaseDate());

        var existing = providerId == null
                ? movieRepository.findBySlug(slug)
                : movieRepository.findByTmdbId(providerId)
                        .or(() -> movieRepository.findBySlug(slug));

        Movie movie = existing.orElseGet(() -> new Movie(source.title(), slug));
        boolean isNew = movie.getId() == null;

        movie.setTmdbId(providerId);
        movie.setTitle(source.title());
        movie.setOriginalTitle(source.originalTitle());
        movie.setOverview(source.overview());
        movie.setReleaseDate(source.releaseDate());
        movie.setRuntimeMinutes(source.runtimeMinutes());
        movie.setOriginalLanguage(source.originalLanguage());
        movie.setOriginCountry(source.originCountry());
        movie.setPosterUrl(source.posterUrl());
        movie.setBackdropUrl(source.backdropUrl());
        if (source.trailerUrl() != null) {
            movie.setTrailerUrl(source.trailerUrl());
        }
        movie.setExternalRating(BigDecimal.valueOf(source.rating()));
        movie.setExternalVoteCount(source.voteCount());
        movie.setPopularity(BigDecimal.valueOf(source.popularity()));
        movie.setAdult(source.adult());
        if (!source.productionCompanies().isEmpty()) {
            movie.setProductionCompanies(String.join(", ", source.productionCompanies()));
        }

        source.genres().forEach(name -> movie.addGenre(resolveGenre(name)));
        source.keywords().forEach(name -> movie.addKeyword(resolveKeyword(name)));

        if (isNew) {
            // Credits are only attached on insert. Rebuilding them on every
            // update would churn rows for data that effectively never changes.
            attachCredits(movie, source);
        }

        movieRepository.save(movie);
        return isNew;
    }

    private void attachCredits(Movie movie, ProviderMovie source) {
        int order = 0;
        for (String name : source.cast()) {
            movie.addCredit(new MovieCredit(resolvePerson(name), CreditType.CAST, order++));
        }
        order = 0;
        for (String name : source.directors()) {
            var credit = new MovieCredit(resolvePerson(name), CreditType.DIRECTOR, order++);
            credit.setJob("Director");
            movie.addCredit(credit);
        }
        order = 0;
        for (String name : source.writers()) {
            var credit = new MovieCredit(resolvePerson(name), CreditType.WRITER, order++);
            credit.setJob("Screenplay");
            movie.addCredit(credit);
        }
    }

    private Genre resolveGenre(String name) {
        return genreRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> genreRepository.save(new Genre(null, name, slugifyName(name))));
    }

    private Keyword resolveKeyword(String name) {
        return keywordRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> keywordRepository.save(new Keyword(name)));
    }

    private Person resolvePerson(String name) {
        return personRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> personRepository.save(new Person(name)));
    }

    /** Reports which provider is active, for the admin UI and diagnostics. */
    public Map<String, Object> providerStatus() {
        return Map.of(
                "provider", provider.name(),
                "available", provider.isAvailable(),
                "catalogueSize", movieRepository.count());
    }

    private static Integer parseProviderId(String providerId) {
        try {
            return providerId == null ? null : Integer.valueOf(providerId);
        } catch (NumberFormatException notNumeric) {
            // Local provider ids are database ids; non-numeric ids belong to a
            // provider whose namespace does not map onto tmdb_id.
            return null;
        }
    }

    private static String slugify(String title, java.time.LocalDate releaseDate) {
        String base = slugifyName(title);
        return releaseDate == null ? base : base + "-" + releaseDate.getYear();
    }

    private static String slugifyName(String value) {
        String normalised = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return normalised.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
