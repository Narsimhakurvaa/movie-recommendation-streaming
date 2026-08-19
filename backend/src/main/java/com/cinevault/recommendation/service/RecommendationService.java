package com.cinevault.recommendation.service;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import com.cinevault.catalogue.repository.GenreRepository;
import com.cinevault.catalogue.repository.KeywordRepository;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.catalogue.repository.PersonRepository;
import com.cinevault.catalogue.service.MovieMapper;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.RecommendationLog;
import com.cinevault.interaction.repository.RecommendationLogRepository;
import com.cinevault.recommendation.dto.RecommendationDtos.RecommendationItem;
import com.cinevault.recommendation.explain.ExplanationBuilder;
import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.UserTasteProfile;
import com.cinevault.recommendation.strategy.ColdStartRecommendationStrategy;
import com.cinevault.recommendation.strategy.CollaborativeRecommendationStrategy;
import com.cinevault.recommendation.strategy.ContentBasedRecommendationStrategy;
import com.cinevault.recommendation.strategy.HybridRecommendationStrategy;
import com.cinevault.recommendation.strategy.PopularityRecommendationStrategy;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Orchestrates recommendation generation.
 *
 * <p>Responsibilities are deliberately narrow: load data, choose a strategy,
 * delegate scoring to the framework-free engine, then map and persist the
 * result. No ranking arithmetic lives here - that all sits in the strategies,
 * which is what makes the algorithm testable without Spring.
 *
 * <h2>Strategy selection</h2>
 * <ul>
 *   <li>Cold-start users (fewer than three ratings and little history) are
 *       routed to {@link ColdStartRecommendationStrategy}.</li>
 *   <li>Everyone else gets the {@link HybridRecommendationStrategy}, which
 *       internally renormalises its weights over whichever components can
 *       actually contribute.</li>
 * </ul>
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /** Scored candidates are capped; see {@link MovieFeatureLoader}. */
    private final int candidatePoolSize;

    private final MovieFeatureLoader featureLoader;
    private final TasteProfileAssembler profileAssembler;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final KeywordRepository keywordRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final UserRepository userRepository;
    private final MovieMapper movieMapper;
    private final Clock clock;

    public RecommendationService(MovieFeatureLoader featureLoader,
                                 TasteProfileAssembler profileAssembler,
                                 MovieRepository movieRepository,
                                 GenreRepository genreRepository,
                                 PersonRepository personRepository,
                                 KeywordRepository keywordRepository,
                                 RecommendationLogRepository recommendationLogRepository,
                                 UserRepository userRepository,
                                 MovieMapper movieMapper,
                                 Clock clock,
                                 @Value("${cinevault.recommendation.candidate-pool-size:500}")
                                 int candidatePoolSize) {
        this.featureLoader = featureLoader;
        this.profileAssembler = profileAssembler;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository;
        this.keywordRepository = keywordRepository;
        this.recommendationLogRepository = recommendationLogRepository;
        this.userRepository = userRepository;
        this.movieMapper = movieMapper;
        this.clock = clock;
        this.candidatePoolSize = candidatePoolSize;
    }

    /**
     * Personalised recommendations for a signed-in user.
     *
     * @param userId the user to serve
     * @param page   zero-based page index
     * @param size   page size
     */
    @Transactional(readOnly = true)
    public PageResponse<RecommendationItem> recommendFor(Long userId, int page, int size) {
        UserTasteProfile profile = profileAssembler.assemble(userId);
        List<MovieFeatures> pool = featureLoader.loadCandidatePool(
                candidatePoolSize, profile.includeAdult());

        if (pool.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        // Never recommend something the user has already engaged with.
        List<MovieFeatures> candidates = pool.stream()
                .filter(m -> !profile.interactedMovieIds().contains(m.movieId()))
                .filter(m -> !profile.ratings().containsKey(m.movieId()))
                .filter(m -> m.externalRating() >= profile.minimumRating())
                .toList();

        if (candidates.isEmpty()) {
            // Everything in the pool has been seen; fall back to the raw pool
            // rather than returning nothing.
            candidates = pool;
        }

        NameResolvers resolvers = buildResolvers();
        boolean coldStart = profile.isColdStart();
        RecommendationType type = coldStart
                ? RecommendationType.COLD_START : RecommendationType.HYBRID;

        List<ScoredMovie> ranked = coldStart
                ? coldStartStrategy(resolvers).score(profile, candidates)
                : hybridStrategy(profile, resolvers).score(profile, candidates);

        ranked.sort(ScoredMovie.byScoreDescending());
        log.debug("Generated {} recommendations for user {} using {}",
                ranked.size(), userId, type);

        return paginate(ranked, type, page, size, userId);
    }

    /**
     * "More like this" for a specific film. Works for anonymous callers, so no
     * profile is consulted.
     */
    @Transactional(readOnly = true)
    public PageResponse<RecommendationItem> findSimilar(Long movieId, int page, int size) {
        MovieFeatures seed = featureLoader.loadOne(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", movieId));

        List<MovieFeatures> pool = featureLoader.loadCandidatePool(candidatePoolSize, false);
        NameResolvers resolvers = buildResolvers();

        List<ScoredMovie> ranked = hybridStrategy(UserTasteProfile.empty(0L), resolvers)
                .findSimilar(seed, pool, resolvers.genreNames(), resolvers.personNames());

        return paginate(ranked, RecommendationType.SIMILAR, page, size, null);
    }

    /**
     * Popularity-ranked recommendations for anonymous visitors.
     *
     * <p>The home page must be useful before anyone signs in, and this is the
     * same {@link PopularityRecommendationStrategy} the hybrid blend uses, so
     * signed-out and signed-in feeds stay consistent in character.
     */
    @Transactional(readOnly = true)
    public PageResponse<RecommendationItem> recommendForAnonymous(int page, int size) {
        List<MovieFeatures> pool = featureLoader.loadCandidatePool(candidatePoolSize, false);
        if (pool.isEmpty()) {
            return PageResponse.empty(page, size);
        }
        NameResolvers resolvers = buildResolvers();
        List<ScoredMovie> ranked =
                coldStartStrategy(resolvers).score(UserTasteProfile.empty(0L), pool);
        ranked.sort(ScoredMovie.byScoreDescending());
        return paginate(ranked, RecommendationType.COLD_START, page, size, null);
    }

    /**
     * Persists served recommendations.
     *
     * <p>Runs in its own transaction because the read path is
     * {@code readOnly = true}; without {@code REQUIRES_NEW} the insert would
     * fail against a read-only transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordServed(Long userId, List<RecommendationItem> items) {
        if (userId == null || items.isEmpty()) {
            return;
        }
        var user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        List<RecommendationLog> logs = new ArrayList<>(items.size());
        for (RecommendationItem item : items) {
            Movie movie = movieRepository.getReferenceById(item.movie().id());
            logs.add(new RecommendationLog(user, movie, item.recommendationType(),
                    BigDecimal.valueOf(item.score()).setScale(4, RoundingMode.HALF_UP),
                    truncate(item.reason())));
        }
        recommendationLogRepository.saveAll(logs);
    }

    // ---------------------------------------------------------------- internals

    private ColdStartRecommendationStrategy coldStartStrategy(NameResolvers resolvers) {
        return new ColdStartRecommendationStrategy(
                new PopularityRecommendationStrategy(clock), resolvers.genreNames());
    }

    private HybridRecommendationStrategy hybridStrategy(UserTasteProfile profile,
                                                        NameResolvers resolvers) {
        var content = new ContentBasedRecommendationStrategy(
                resolvers.genreNames(), resolvers.personNames(), resolvers.keywordNames());
        var collaborative = new CollaborativeRecommendationStrategy(
                userId -> profileAssembler.loadNeighbourRatings(profile.userId()));
        var popularity = new PopularityRecommendationStrategy(clock);
        return new HybridRecommendationStrategy(content, collaborative, popularity);
    }

    /**
     * Loads display names once per request.
     *
     * <p>These tables are small and change only on data import, so loading them
     * whole is cheaper than repeated targeted lookups while scoring.
     */
    private NameResolvers buildResolvers() {
        Map<Long, String> genreNames = new HashMap<>();
        genreRepository.findAll().forEach(g -> genreNames.put(g.getId(), g.getName()));

        Map<Long, String> keywordNames = new HashMap<>();
        keywordRepository.findAll().forEach(k -> keywordNames.put(k.getId(), k.getName()));

        Map<Long, String> personNames = new HashMap<>();
        personRepository.findAll().forEach(p -> personNames.put(p.getId(), p.getName()));

        return new NameResolvers(genreNames::get, keywordNames::get, personNames::get);
    }

    /**
     * Slices the ranked list, then hydrates only that slice into DTOs.
     *
     * <p>Mapping happens after pagination on purpose: a 500-candidate pool
     * produces 500 scores but only ~20 need entity loading and mapping.
     */
    private PageResponse<RecommendationItem> paginate(List<ScoredMovie> ranked,
                                                      RecommendationType type,
                                                      int page, int size, Long userId) {
        int from = Math.min(page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());
        List<ScoredMovie> slice = ranked.subList(from, to);

        if (slice.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, ranked.size(),
                    (int) Math.ceil((double) ranked.size() / size), page == 0, true);
        }

        List<Long> movieIds = slice.stream().map(ScoredMovie::movieId).toList();
        Map<Long, Movie> movies = new HashMap<>();
        movieRepository.findAllWithGenresByIdIn(movieIds)
                .forEach(movie -> movies.put(movie.getId(), movie));

        List<RecommendationItem> items = new ArrayList<>(slice.size());
        for (ScoredMovie scored : slice) {
            Movie movie = movies.get(scored.movieId());
            if (movie == null) {
                continue; // removed between scoring and hydration
            }
            MovieSummary summary = movieMapper.toSummary(movie);
            items.add(new RecommendationItem(summary,
                    round(scored.score()),
                    ExplanationBuilder.describe(scored, type),
                    type.name()));
        }

        int totalPages = (int) Math.ceil((double) ranked.size() / size);
        return new PageResponse<>(items, page, size, ranked.size(), totalPages,
                page == 0, to >= ranked.size());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }

    /** Bundle of id-to-name resolvers handed to the engine. */
    private record NameResolvers(LongFunction<String> genreNames,
                                 LongFunction<String> keywordNames,
                                 LongFunction<String> personNames) {
    }
}
