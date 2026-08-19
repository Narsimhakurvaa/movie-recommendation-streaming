package com.cinevault.catalogue.service;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.dto.MovieDtos.GenreResponse;
import com.cinevault.catalogue.dto.MovieDtos.MovieDetail;
import com.cinevault.catalogue.dto.MovieDtos.MovieSuggestionResponse;
import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import com.cinevault.catalogue.dto.MovieSearchCriteria;
import com.cinevault.catalogue.repository.GenreRepository;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.catalogue.repository.MovieSpecifications;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.interaction.repository.WatchlistRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalogue queries: discovery, search, detail and curated rails.
 *
 * <h2>Personal state without N+1</h2>
 * <p>Listings need to know, per card, whether the signed-in user has saved or
 * rated that film. Asking per card would issue one query per row. Instead the
 * page is fetched first, then two bulk queries resolve watchlist membership and
 * ratings for the whole page at once - three queries total, independent of page
 * size. See {@link #applyUserState}.
 */
@Service
@Transactional(readOnly = true)
public class MovieService {

    /** Minimum votes before a film may appear in "top rated". */
    private static final int TOP_RATED_MIN_VOTES = 500;

    /** Upper bound on page size so a caller cannot request the whole table. */
    private static final int MAX_PAGE_SIZE = 100;

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final WatchlistRepository watchlistRepository;
    private final RatingRepository ratingRepository;
    private final MovieMapper movieMapper;

    public MovieService(MovieRepository movieRepository,
                        GenreRepository genreRepository,
                        WatchlistRepository watchlistRepository,
                        RatingRepository ratingRepository,
                        MovieMapper movieMapper) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.watchlistRepository = watchlistRepository;
        this.ratingRepository = ratingRepository;
        this.movieMapper = movieMapper;
    }

    /**
     * Discovery with arbitrary filter combinations.
     *
     * @param criteria filters and sort order
     * @param pageable page request; size is capped
     * @param userId   signed-in user, or {@code null} for anonymous callers
     */
    public PageResponse<MovieSummary> discover(MovieSearchCriteria criteria,
                                               Pageable pageable,
                                               Long userId) {
        Specification<Movie> specification = buildSpecification(criteria);
        Pageable effective = withSort(pageable, criteria);

        Page<Movie> page = movieRepository.findAll(specification, effective);
        return toPageResponse(page, userId);
    }

    /**
     * Search by title. A thin wrapper over {@link #discover} so that filters and
     * sorting behave identically whether or not a query term is present.
     */
    public PageResponse<MovieSummary> search(MovieSearchCriteria criteria,
                                             Pageable pageable,
                                             Long userId) {
        return discover(criteria, pageable, userId);
    }

    /**
     * Type-ahead suggestions.
     *
     * <p>Cached because the same short prefixes are requested constantly and the
     * underlying data changes rarely.
     */
    @Cacheable(cacheNames = "movieSuggestions", key = "#prefix.toLowerCase() + '-' + #limit")
    public List<MovieSuggestionResponse> suggest(String prefix, int limit) {
        if (prefix == null || prefix.trim().length() < 2) {
            return List.of();
        }
        return movieRepository.suggest(prefix.trim(), PageRequest.of(0, Math.min(limit, 20)))
                .stream()
                .map(s -> new MovieSuggestionResponse(
                        s.id(), s.title(), s.slug(), s.posterUrl(), s.releaseYear()))
                .toList();
    }

    /** Full detail for one film, including the caller's own state. */
    public MovieDetail findById(Long movieId, Long userId) {
        Movie movie = movieRepository.findDetailById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", movieId));
        return toDetailWithUserState(movie, userId);
    }

    /** Full detail by slug, for shareable URLs. */
    public MovieDetail findBySlug(String slug, Long userId) {
        Movie movie = movieRepository.findDetailBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", slug));
        return toDetailWithUserState(movie, userId);
    }

    private MovieDetail toDetailWithUserState(Movie movie, Long userId) {
        Boolean saved = null;
        Integer rating = null;
        if (userId != null) {
            saved = watchlistRepository.existsByUserIdAndMovieId(userId, movie.getId());
            rating = ratingRepository.findByUserIdAndMovieId(userId, movie.getId())
                    .map(r -> (int) r.getScore())
                    .orElse(null);
        }
        return movieMapper.toDetail(movie, saved, rating);
    }

    public PageResponse<MovieSummary> findTrending(Pageable pageable, Long userId) {
        // "Recent" is a rolling 18-month window: long enough to stay populated
        // between release seasons, short enough that the rail feels current.
        var since = java.time.LocalDate.now().minusMonths(18);
        return toPageResponse(movieRepository.findTrending(since, capped(pageable)), userId);
    }

    public PageResponse<MovieSummary> findPopular(Pageable pageable, Long userId) {
        return toPageResponse(movieRepository.findPopular(capped(pageable)), userId);
    }

    public PageResponse<MovieSummary> findTopRated(Pageable pageable, Long userId) {
        return toPageResponse(
                movieRepository.findTopRated(TOP_RATED_MIN_VOTES, capped(pageable)), userId);
    }

    public PageResponse<MovieSummary> findNewReleases(Pageable pageable, Long userId) {
        return toPageResponse(
                movieRepository.findRecentReleases(java.time.LocalDate.now(), capped(pageable)),
                userId);
    }

    /** Films in one genre, reusing the discovery pipeline. */
    public PageResponse<MovieSummary> findByGenre(String genreSlug, Pageable pageable, Long userId) {
        genreRepository.findBySlug(genreSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Genre", genreSlug));
        var criteria = new MovieSearchCriteria(null, List.of(genreSlug), false, null, null,
                null, null, null, null, null, false, null);
        return discover(criteria, pageable, userId);
    }

    /** Genre list with counts, cached because it changes only on data import. */
    @Cacheable(cacheNames = "genres")
    public List<GenreResponse> findAllGenres() {
        return genreRepository.findAllWithMovieCounts().stream()
                .map(g -> new GenreResponse(g.getId(), g.getName(), g.getSlug(), g.getTotal()))
                .toList();
    }

    private Specification<Movie> buildSpecification(MovieSearchCriteria criteria) {
        Specification<Movie> genreSpec = criteria.matchAllGenres()
                ? MovieSpecifications.hasAllGenreSlugs(criteria.genres())
                : MovieSpecifications.hasAnyGenreSlug(criteria.genres());

        return MovieSpecifications.allOf(
                MovieSpecifications.titleContains(criteria.query()),
                genreSpec,
                MovieSpecifications.releasedFrom(criteria.yearFrom()),
                MovieSpecifications.releasedUntil(criteria.yearTo()),
                MovieSpecifications.ratedAtLeast(criteria.minRating()),
                MovieSpecifications.ratedAtMost(criteria.maxRating()),
                MovieSpecifications.inLanguages(criteria.languages()),
                MovieSpecifications.runtimeBetween(criteria.minRuntime(), criteria.maxRuntime()),
                MovieSpecifications.adultVisibility(criteria.includeAdult()));
    }

    /** Applies the allowlisted sort, ignoring any client-supplied ordering. */
    private Pageable withSort(Pageable pageable, MovieSearchCriteria criteria) {
        return PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                criteria.sortOption().sort());
    }

    private Pageable capped(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    private PageResponse<MovieSummary> toPageResponse(Page<Movie> page, Long userId) {
        List<MovieSummary> summaries = page.getContent().stream()
                .map(movieMapper::toSummary)
                .toList();
        return new PageResponse<>(applyUserState(summaries, userId),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    /**
     * Layers the caller's watchlist and rating state onto a page of cards.
     *
     * <p>Two bulk queries for the whole page rather than two per card. For a
     * 20-item page that is 2 queries instead of 40.
     */
    List<MovieSummary> applyUserState(List<MovieSummary> summaries, Long userId) {
        if (userId == null || summaries.isEmpty()) {
            return summaries;
        }
        List<Long> movieIds = summaries.stream().map(MovieSummary::id).toList();

        Set<Long> saved = new HashSet<>(
                watchlistRepository.findSavedMovieIdsAmong(userId, movieIds));

        Map<Long, Integer> ratings = new HashMap<>();
        ratingRepository.findScoresByUserId(userId).forEach(row -> {
            if (movieIds.contains(row.getMovieId())) {
                ratings.put(row.getMovieId(), row.getScore().intValue());
            }
        });

        return summaries.stream()
                .map(summary -> summary.withUserState(
                        saved.contains(summary.id()), ratings.get(summary.id())))
                .toList();
    }
}
