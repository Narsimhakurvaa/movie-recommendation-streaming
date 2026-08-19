package com.cinevault.recommendation.strategy;

import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.SignalContribution;
import com.cinevault.recommendation.model.SignalContribution.SignalKind;
import com.cinevault.recommendation.model.UserTasteProfile;
import com.cinevault.recommendation.scoring.SimilarityFunctions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blends every applicable strategy into a single personalised ranking.
 *
 * <h2>Weighting</h2>
 * <p>The base weights are:
 * <table>
 *   <caption>Base blend weights</caption>
 *   <tr><th>Component</th><th>Weight</th><th>Reasoning</th></tr>
 *   <tr><td>Content</td><td>0.40</td>
 *       <td>Highest because it is the most explainable and most robust signal:
 *           it works from the first rating, degrades gracefully, and never
 *           depends on other users existing.</td></tr>
 *   <tr><td>Collaborative</td><td>0.35</td>
 *       <td>Highest ceiling - it surfaces titles content similarity cannot
 *           reach - but needs data, so it sits just below content.</td></tr>
 *   <tr><td>Popularity</td><td>0.15</td>
 *       <td>A quality floor. Kept deliberately low: too much and every user
 *           receives the same homogeneous feed.</td></tr>
 *   <tr><td>Preference</td><td>0.10</td>
 *       <td>Explicit onboarding picks. Small, because stated preferences are
 *           consistently less predictive than observed behaviour.</td></tr>
 * </table>
 *
 * <h2>Adaptive renormalisation</h2>
 * <p>Weights are re-normalised across only the strategies that
 * {@linkplain RecommendationStrategy#supports can contribute} for this user.
 * A user with two ratings gets no collaborative component, and rather than
 * losing 35% of the possible score (which would flatten their ranking towards
 * popularity), the remaining weights are scaled up to sum to 1.0 again. This is
 * what keeps scores comparable across users with very different histories.
 *
 * <h2>Exploration</h2>
 * <p>A small deterministic diversity bonus rewards candidates whose genres are
 * under-represented in the results assembled so far. It is applied after the
 * blend and is intentionally tiny, enough to break up monotony without
 * meaningfully overriding relevance.
 */
public class HybridRecommendationStrategy implements RecommendationStrategy {

    static final double CONTENT_WEIGHT = 0.40;
    static final double COLLABORATIVE_WEIGHT = 0.35;
    static final double POPULARITY_WEIGHT = 0.15;
    static final double PREFERENCE_WEIGHT = 0.10;

    /** Maximum uplift from the diversity bonus. */
    static final double DIVERSITY_BONUS = 0.04;

    /** Watchlisted titles are things the user already told us they want. */
    static final double WATCHLIST_BOOST = 0.05;

    private final ContentBasedRecommendationStrategy contentStrategy;
    private final CollaborativeRecommendationStrategy collaborativeStrategy;
    private final PopularityRecommendationStrategy popularityStrategy;

    public HybridRecommendationStrategy(ContentBasedRecommendationStrategy contentStrategy,
                                        CollaborativeRecommendationStrategy collaborativeStrategy,
                                        PopularityRecommendationStrategy popularityStrategy) {
        this.contentStrategy = contentStrategy;
        this.collaborativeStrategy = collaborativeStrategy;
        this.popularityStrategy = popularityStrategy;
    }

    @Override
    public List<ScoredMovie> score(UserTasteProfile profile, Collection<MovieFeatures> candidates) {
        // Decide which components can speak for this user, then renormalise.
        Map<RecommendationType, Double> weights = resolveWeights(profile);

        Map<Long, ScoredMovie> merged = new LinkedHashMap<>();
        for (MovieFeatures movie : candidates) {
            merged.put(movie.movieId(), new ScoredMovie(movie));
        }

        if (weights.containsKey(RecommendationType.CONTENT_BASED)) {
            accumulate(merged, contentStrategy.score(profile, candidates),
                    weights.get(RecommendationType.CONTENT_BASED));
        }
        if (weights.containsKey(RecommendationType.COLLABORATIVE)) {
            accumulate(merged, collaborativeStrategy.score(profile, candidates),
                    weights.get(RecommendationType.COLLABORATIVE));
        }
        if (weights.containsKey(RecommendationType.POPULARITY)) {
            accumulate(merged, popularityStrategy.score(profile, candidates),
                    weights.get(RecommendationType.POPULARITY));
        }
        applyPreferenceComponent(profile, merged, weights);

        List<ScoredMovie> results = new ArrayList<>(merged.values());
        applyWatchlistBoost(profile, results);
        results.sort(ScoredMovie.byScoreDescending());
        return applyDiversityBonus(results);
    }

    /**
     * Renormalises the base weights over the applicable strategies so they
     * always sum to 1.0.
     *
     * <p>Public because the admin diagnostics endpoint reports which components
     * were active for a given user, which is the single most useful piece of
     * information when investigating "why was I recommended this?".
     *
     * @return an ordered map of active component to its effective weight
     */
    public Map<RecommendationType, Double> resolveWeights(UserTasteProfile profile) {
        Map<RecommendationType, Double> applicable = new LinkedHashMap<>();
        if (contentStrategy.supports(profile)) {
            applicable.put(RecommendationType.CONTENT_BASED, CONTENT_WEIGHT);
        }
        if (collaborativeStrategy.supports(profile)) {
            applicable.put(RecommendationType.COLLABORATIVE, COLLABORATIVE_WEIGHT);
        }
        // Popularity always applies - it is the floor that guarantees results.
        applicable.put(RecommendationType.POPULARITY, POPULARITY_WEIGHT);
        if (!profile.declaredGenreIds().isEmpty()) {
            applicable.put(RecommendationType.COLD_START, PREFERENCE_WEIGHT);
        }

        double total = applicable.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return Map.of(RecommendationType.POPULARITY, 1.0d);
        }
        Map<RecommendationType, Double> normalised = new LinkedHashMap<>();
        applicable.forEach((type, weight) -> normalised.put(type, weight / total));
        return normalised;
    }

    private void accumulate(Map<Long, ScoredMovie> merged, List<ScoredMovie> partial, double weight) {
        for (ScoredMovie scored : partial) {
            ScoredMovie target = merged.get(scored.movieId());
            if (target == null) {
                continue;
            }
            target.addScore(scored.score() * weight);
            target.addContributions(scored.contributions());
        }
    }

    /**
     * Explicit onboarding genres, scored directly rather than by delegating to
     * the cold-start strategy, whose diversification pass is inappropriate here.
     */
    private void applyPreferenceComponent(UserTasteProfile profile,
                                          Map<Long, ScoredMovie> merged,
                                          Map<RecommendationType, Double> weights) {
        Double weight = weights.get(RecommendationType.COLD_START);
        if (weight == null || profile.declaredGenreIds().isEmpty()) {
            return;
        }
        for (ScoredMovie scored : merged.values()) {
            var genres = scored.movie().genreIds();
            if (genres.isEmpty()) {
                continue;
            }
            long matches = genres.stream().filter(profile.declaredGenreIds()::contains).count();
            if (matches == 0) {
                continue;
            }
            double preference = (double) matches / genres.size();
            scored.addScore(preference * weight);
        }
    }

    /** Nudges saved titles up; the user has already signalled intent to watch. */
    private void applyWatchlistBoost(UserTasteProfile profile, List<ScoredMovie> results) {
        if (profile.watchlistMovieIds().isEmpty()) {
            return;
        }
        for (ScoredMovie scored : results) {
            if (profile.watchlistMovieIds().contains(scored.movieId())) {
                scored.addScore(WATCHLIST_BOOST);
            }
        }
    }

    /**
     * Rewards genres that have appeared less often so far in the ranked list.
     *
     * <p>Deterministic (no randomness) so results are reproducible and testable.
     * The bonus decays with how many times a genre has already been seen.
     */
    private List<ScoredMovie> applyDiversityBonus(List<ScoredMovie> ranked) {
        Map<Long, Integer> genreSeen = new HashMap<>();
        for (ScoredMovie scored : ranked) {
            var genres = scored.movie().genreIds();
            if (genres.isEmpty()) {
                continue;
            }
            int maxSeen = genres.stream()
                    .mapToInt(g -> genreSeen.getOrDefault(g, 0))
                    .max().orElse(0);
            double bonus = DIVERSITY_BONUS / (1.0d + maxSeen);
            scored.addScore(bonus);
            genres.forEach(g -> genreSeen.merge(g, 1, Integer::sum));
        }
        ranked.sort(ScoredMovie.byScoreDescending());
        for (ScoredMovie scored : ranked) {
            scored.setScore(SimilarityFunctions.clamp01(scored.score()));
        }
        return ranked;
    }

    /**
     * "More like this" for a specific seed film, used by
     * {@code /api/recommendations/similar/{movieId}}.
     *
     * <p>Pure metadata similarity against the seed, with a light quality tilt so
     * that among equally similar titles the better-regarded one wins. The user
     * profile is not consulted, which keeps the endpoint meaningful for
     * anonymous visitors.
     */
    public List<ScoredMovie> findSimilar(MovieFeatures seed,
                                         Collection<MovieFeatures> candidates,
                                         java.util.function.LongFunction<String> genreNames,
                                         java.util.function.LongFunction<String> personNames) {
        List<ScoredMovie> results = new ArrayList<>();
        for (MovieFeatures candidate : candidates) {
            if (candidate.movieId() == seed.movieId()) {
                continue;
            }
            double genre = SimilarityFunctions.jaccard(seed.genreIds(), candidate.genreIds());
            double keyword = SimilarityFunctions.jaccard(seed.keywordIds(), candidate.keywordIds());
            double cast = SimilarityFunctions.jaccard(seed.castIds(), candidate.castIds());
            boolean sameDirector = candidate.directorIds().stream()
                    .anyMatch(seed.directorIds()::contains);
            boolean sameLanguage = seed.language() != null
                    && seed.language().equals(candidate.language());

            double similarity = (genre * 0.35)
                    + (keyword * 0.30)
                    + (cast * 0.15)
                    + (sameDirector ? 0.15 : 0d)
                    + (sameLanguage ? 0.05 : 0d);
            if (similarity <= 0) {
                continue;
            }
            // Light quality tilt: 85% similarity, 15% shrunk rating.
            double quality = SimilarityFunctions.bayesianAverage(
                    candidate.externalRating(), candidate.externalVotes(),
                    PopularityRecommendationStrategy.PRIOR_MEAN_RATING,
                    PopularityRecommendationStrategy.VOTE_CONFIDENCE) / 10d;
            double score = SimilarityFunctions.clamp01((similarity * 0.85) + (quality * 0.15));

            ScoredMovie scored = new ScoredMovie(candidate, score);
            if (sameDirector) {
                candidate.directorIds().stream()
                        .filter(seed.directorIds()::contains)
                        .findFirst()
                        .ifPresent(id -> {
                            String name = personNames == null ? null : personNames.apply(id);
                            if (name != null) {
                                scored.addContribution(SignalContribution.of(
                                        SignalKind.SHARED_DIRECTOR, 0.9, name, id));
                            }
                        });
            }
            if (genre > 0) {
                candidate.genreIds().stream()
                        .filter(seed.genreIds()::contains)
                        .findFirst()
                        .ifPresent(id -> {
                            String name = genreNames == null ? null : genreNames.apply(id);
                            if (name != null) {
                                scored.addContribution(SignalContribution.of(
                                        SignalKind.SHARED_GENRE, genre, name, id));
                            }
                        });
            }
            if (scored.contributions().isEmpty()) {
                scored.addContribution(SignalContribution.of(
                        SignalKind.SIMILAR_TO_LIKED, similarity, seed.title(), seed.movieId()));
            }
            results.add(scored);
        }
        results.sort(ScoredMovie.byScoreDescending());
        return results;
    }

    @Override
    public RecommendationType type() {
        return RecommendationType.HYBRID;
    }
}
