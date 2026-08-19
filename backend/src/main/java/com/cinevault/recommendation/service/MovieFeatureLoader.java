package com.cinevault.recommendation.service;

import com.cinevault.catalogue.domain.CreditType;
import com.cinevault.catalogue.domain.Genre;
import com.cinevault.catalogue.domain.Keyword;
import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.domain.MovieCredit;
import com.cinevault.catalogue.repository.MovieCreditRepository;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.recommendation.model.MovieFeatures;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the candidate pool and converts entities into {@link MovieFeatures}.
 *
 * <h2>Query budget</h2>
 * <p>Building features for N films costs exactly three queries regardless of N:
 * one for the films with their genres, one for keywords, one for all credits.
 * Reading {@code movie.getCredits()} per film instead would be N+1.
 *
 * <h2>Pool size</h2>
 * <p>The pool is bounded rather than the whole catalogue. Personalisation
 * reorders candidates, it does not rescue a film from the far tail of
 * popularity, so scoring everything would burn CPU without changing the top of
 * the list. The bound is configurable and sized so that even a heavily filtered
 * request still has plenty to rank.
 */
@Component
public class MovieFeatureLoader {

    private final MovieRepository movieRepository;
    private final MovieCreditRepository creditRepository;

    public MovieFeatureLoader(MovieRepository movieRepository,
                              MovieCreditRepository creditRepository) {
        this.movieRepository = movieRepository;
        this.creditRepository = creditRepository;
    }

    /**
     * Loads the top {@code poolSize} films by popularity as scoring candidates.
     *
     * @param poolSize     maximum candidates to consider
     * @param includeAdult whether adult titles may be included
     */
    @Transactional(readOnly = true)
    public List<MovieFeatures> loadCandidatePool(int poolSize, boolean includeAdult) {
        List<Movie> movies = movieRepository.findCandidatePool(
                includeAdult, PageRequest.of(0, poolSize));
        return toFeatures(movies);
    }

    /** Loads features for a specific set of films. */
    @Transactional(readOnly = true)
    public List<MovieFeatures> loadByIds(Collection<Long> movieIds) {
        if (movieIds.isEmpty()) {
            return List.of();
        }
        return toFeatures(movieRepository.findAllWithGenresByIdIn(movieIds));
    }

    /** Loads features for exactly one film, or empty when it does not exist. */
    @Transactional(readOnly = true)
    public java.util.Optional<MovieFeatures> loadOne(Long movieId) {
        return loadByIds(List.of(movieId)).stream().findFirst();
    }

    /**
     * Converts entities to features, resolving keywords and credits in bulk.
     */
    private List<MovieFeatures> toFeatures(List<Movie> movies) {
        if (movies.isEmpty()) {
            return List.of();
        }
        List<Long> movieIds = movies.stream().map(Movie::getId).toList();

        // One query for every credit across the whole pool.
        Map<Long, List<MovieCredit>> creditsByMovie = new HashMap<>();
        for (MovieCredit credit : creditRepository.findAllByMovieIdIn(movieIds)) {
            creditsByMovie.computeIfAbsent(credit.getMovie().getId(), id -> new ArrayList<>())
                    .add(credit);
        }

        List<MovieFeatures> features = new ArrayList<>(movies.size());
        for (Movie movie : movies) {
            List<MovieCredit> credits = creditsByMovie.getOrDefault(movie.getId(), List.of());
            features.add(new MovieFeatures(
                    movie.getId(),
                    movie.getTitle(),
                    movie.getGenres().stream().map(Genre::getId).collect(java.util.stream.Collectors.toSet()),
                    movie.getKeywords().stream().map(Keyword::getId).collect(java.util.stream.Collectors.toSet()),
                    personIds(credits, CreditType.CAST),
                    personIds(credits, CreditType.DIRECTOR),
                    personIds(credits, CreditType.WRITER),
                    movie.getOriginalLanguage(),
                    movie.getReleaseDate(),
                    movie.getExternalRating() == null ? 0d : movie.getExternalRating().doubleValue(),
                    movie.getExternalVoteCount(),
                    movie.getAverageRating() == null ? 0d : movie.getAverageRating().doubleValue(),
                    movie.getRatingCount(),
                    movie.getPopularity() == null ? 0d : movie.getPopularity().doubleValue(),
                    movie.isAdult()));
        }
        return features;
    }

    private static Set<Long> personIds(List<MovieCredit> credits, CreditType type) {
        Set<Long> ids = new HashSet<>();
        for (MovieCredit credit : credits) {
            if (credit.getCreditType() == type) {
                ids.add(credit.getPerson().getId());
            }
        }
        return ids;
    }

    /**
     * Name lookups used when rendering explanations.
     *
     * <p>Returned as maps so the engine can resolve a genre or person name
     * without holding a repository reference, keeping it persistence-free.
     */
    @Transactional(readOnly = true)
    public NameLookups loadNameLookups(Collection<Long> movieIds) {
        Map<Long, String> genreNames = new LinkedHashMap<>();
        Map<Long, String> keywordNames = new LinkedHashMap<>();
        Map<Long, String> personNames = new LinkedHashMap<>();

        if (movieIds.isEmpty()) {
            return new NameLookups(genreNames, keywordNames, personNames);
        }
        for (Movie movie : movieRepository.findAllWithGenresByIdIn(movieIds)) {
            movie.getGenres().forEach(g -> genreNames.put(g.getId(), g.getName()));
        }
        creditRepository.findAllByMovieIdIn(movieIds).forEach(credit ->
                personNames.put(credit.getPerson().getId(), credit.getPerson().getName()));
        return new NameLookups(genreNames, keywordNames, personNames);
    }

    /** Identifier-to-display-name maps for explanation rendering. */
    public record NameLookups(Map<Long, String> genreNames,
                              Map<Long, String> keywordNames,
                              Map<Long, String> personNames) {
    }
}
