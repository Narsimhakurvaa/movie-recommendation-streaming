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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Serves users the engine knows little or nothing about.
 *
 * <p>The guiding rule is that a new user must never see an empty page, and
 * should see something better than a raw popularity dump whenever they have
 * given us even a single hint.
 *
 * <p>Three tiers, in decreasing order of personalisation:
 * <ol>
 *   <li><b>Declared genres.</b> If onboarding captured favourite genres, films
 *       matching them are boosted heavily. This is the highest-value signal a
 *       brand-new user can give us, so it dominates the blend at 0.65.</li>
 *   <li><b>A single rating or interaction.</b> Too sparse for collaborative
 *       filtering, but enough to prefer the same genres.</li>
 *   <li><b>Nothing at all.</b> Falls through to pure quality-and-popularity
 *       ranking, with deliberate diversification so the list is not seven
 *       entries from the same franchise.</li>
 * </ol>
 *
 * <h2>Diversity</h2>
 * <p>A greedy genre-capping pass limits how many titles may share a dominant
 * genre. Without it, a user who picks "Science Fiction" receives ten near
 * identical films and learns nothing about the catalogue. Capping trades a
 * little precision for the exploration that cold-start users actually need.
 */
public class ColdStartRecommendationStrategy implements RecommendationStrategy {

    /** Declared-genre match dominates when onboarding data exists. */
    static final double PREFERENCE_WEIGHT = 0.65;
    static final double POPULARITY_WEIGHT = 0.35;

    /** Maximum titles sharing a dominant genre within one result page. */
    static final int MAX_PER_GENRE = 3;

    private final PopularityRecommendationStrategy popularityStrategy;
    private final LongFunction<String> genreNameLookup;

    public ColdStartRecommendationStrategy(PopularityRecommendationStrategy popularityStrategy,
                                           LongFunction<String> genreNameLookup) {
        this.popularityStrategy = popularityStrategy;
        this.genreNameLookup = genreNameLookup;
    }

    @Override
    public List<ScoredMovie> score(UserTasteProfile profile, Collection<MovieFeatures> candidates) {
        List<ScoredMovie> popularity = popularityStrategy.score(profile, candidates);
        Set<Long> declared = profile.declaredGenreIds();

        if (declared.isEmpty()) {
            // No hints whatsoever: quality and popularity, but diversified.
            popularity.forEach(s -> s.addContribution(SignalContribution.of(
                    SignalKind.TRENDING, 0.4, "popular")));
            return diversify(popularity);
        }

        List<ScoredMovie> blended = new ArrayList<>(popularity.size());
        for (ScoredMovie scored : popularity) {
            MovieFeatures movie = scored.movie();

            // Fraction of the film's genres the user explicitly asked for.
            long matches = movie.genreIds().stream().filter(declared::contains).count();
            double proportion = movie.genreIds().isEmpty()
                    ? 0d : (double) matches / movie.genreIds().size();
            // A single strong match should still count; reward presence as well
            // as proportion so a broad film with one wanted genre is not buried.
            final double preferenceScore = matches > 0 ? Math.max(proportion, 0.5) : proportion;

            double total = (preferenceScore * PREFERENCE_WEIGHT)
                    + (scored.score() * POPULARITY_WEIGHT);
            scored.setScore(SimilarityFunctions.clamp01(total));

            if (matches > 0) {
                movie.genreIds().stream()
                        .filter(declared::contains)
                        .findFirst()
                        .ifPresent(genreId -> {
                            String name = genreNameLookup == null ? null : genreNameLookup.apply(genreId);
                            if (name != null && !name.isBlank()) {
                                scored.addContribution(SignalContribution.of(
                                        SignalKind.ONBOARDING_PREFERENCE, preferenceScore, name, genreId));
                            }
                        });
            }
            blended.add(scored);
        }
        return diversify(blended);
    }

    /**
     * Greedy re-ranking that caps how many entries may share a dominant genre.
     *
     * <p>Runs in a single pass over the sorted list: entries exceeding the cap
     * are deferred to the tail rather than discarded, so the result is a
     * permutation of the input and nothing is ever lost.
     */
    private List<ScoredMovie> diversify(List<ScoredMovie> scored) {
        scored.sort(ScoredMovie.byScoreDescending());

        List<ScoredMovie> primary = new ArrayList<>(scored.size());
        List<ScoredMovie> deferred = new ArrayList<>();
        var genreCounts = new java.util.HashMap<Long, Integer>();

        for (ScoredMovie candidate : scored) {
            Long dominant = dominantGenre(candidate.movie());
            if (dominant == null) {
                primary.add(candidate);
                continue;
            }
            int seen = genreCounts.getOrDefault(dominant, 0);
            if (seen < MAX_PER_GENRE) {
                genreCounts.put(dominant, seen + 1);
                primary.add(candidate);
            } else {
                deferred.add(candidate);
            }
        }
        primary.addAll(deferred);
        return primary;
    }

    /** Lowest genre id, used purely as a stable per-film grouping key. */
    private Long dominantGenre(MovieFeatures movie) {
        return movie.genreIds().stream().min(Long::compareTo).orElse(null);
    }

    /** Never recommends something the user has already interacted with. */
    static Set<Long> excluded(UserTasteProfile profile) {
        Set<Long> seen = new HashSet<>(profile.interactedMovieIds());
        seen.addAll(profile.ratings().keySet());
        return seen;
    }

    @Override
    public RecommendationType type() {
        return RecommendationType.COLD_START;
    }

    @Override
    public boolean supports(UserTasteProfile profile) {
        return profile.isColdStart();
    }
}
