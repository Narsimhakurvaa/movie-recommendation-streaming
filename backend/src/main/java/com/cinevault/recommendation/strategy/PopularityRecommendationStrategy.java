package com.cinevault.recommendation.strategy;

import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.SignalContribution;
import com.cinevault.recommendation.model.SignalContribution.SignalKind;
import com.cinevault.recommendation.model.UserTasteProfile;
import com.cinevault.recommendation.scoring.SimilarityFunctions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Scores films on global desirability, independent of who is asking.
 *
 * <p>Three sub-signals are blended:
 * <ul>
 *   <li><b>Quality (0.50)</b> - a Bayesian-shrunk rating. Weighted highest
 *       because a genuinely great film is a safe recommendation for anyone.</li>
 *   <li><b>Popularity (0.30)</b> - the provider's popularity index, log-squashed
 *       because the raw distribution is extremely long-tailed.</li>
 *   <li><b>Recency (0.20)</b> - exponential decay on release date, so the feed
 *       does not calcify into the same canon of classics forever.</li>
 * </ul>
 *
 * <p>This strategy is also the safety net: it is the only one that can score
 * every film in the catalogue regardless of user state, which guarantees the
 * API never returns an empty page.
 */
public class PopularityRecommendationStrategy implements RecommendationStrategy {

    static final double QUALITY_WEIGHT = 0.50;
    static final double POPULARITY_WEIGHT = 0.30;
    static final double RECENCY_WEIGHT = 0.20;

    /**
     * Prior mean for the Bayesian average, on the provider's 0-10 scale. Set to
     * a typical catalogue average so that unrated films settle at "unremarkable"
     * rather than at zero.
     */
    static final double PRIOR_MEAN_RATING = 6.5;

    /**
     * Votes required before a film's own average outweighs the prior. Chosen so
     * that a handful of votes barely moves the needle but a few thousand
     * effectively takes over.
     */
    static final double VOTE_CONFIDENCE = 1_000d;

    /** Two years: recent enough to feel current, slow enough to avoid churn. */
    static final double RECENCY_HALF_LIFE_DAYS = 730d;

    /** Popularity index value treated as the reference point for squashing. */
    static final double POPULARITY_SCALE = 50d;

    /** Films at or above this shrunk rating are called out as acclaimed. */
    private static final double ACCLAIM_THRESHOLD = 7.5;

    private final Clock clock;

    public PopularityRecommendationStrategy(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<ScoredMovie> score(UserTasteProfile profile, Collection<MovieFeatures> candidates) {
        LocalDate today = LocalDate.now(clock);
        List<ScoredMovie> results = new ArrayList<>(candidates.size());

        for (MovieFeatures movie : candidates) {
            double quality = qualityScore(movie);
            double popularity = SimilarityFunctions.logNormalise(movie.popularity(), POPULARITY_SCALE);
            double recency = recencyScore(movie, today);

            double total = (quality * QUALITY_WEIGHT)
                    + (popularity * POPULARITY_WEIGHT)
                    + (recency * RECENCY_WEIGHT);

            ScoredMovie scored = new ScoredMovie(movie, SimilarityFunctions.clamp01(total));

            double shrunk = SimilarityFunctions.bayesianAverage(
                    movie.externalRating(), movie.externalVotes(), PRIOR_MEAN_RATING, VOTE_CONFIDENCE);
            if (shrunk >= ACCLAIM_THRESHOLD) {
                scored.addContribution(SignalContribution.of(SignalKind.HIGHLY_RATED, quality, "acclaimed"));
            }
            if (recency > 0.5) {
                scored.addContribution(SignalContribution.of(SignalKind.RECENT_RELEASE, recency, "recent"));
            }
            if (popularity > 0.6) {
                scored.addContribution(SignalContribution.of(SignalKind.TRENDING, popularity, "trending"));
            }
            results.add(scored);
        }
        return results;
    }

    /** Bayesian-shrunk provider rating, rescaled from 0-10 to {@code [0,1]}. */
    private double qualityScore(MovieFeatures movie) {
        double shrunk = SimilarityFunctions.bayesianAverage(
                movie.externalRating(), movie.externalVotes(), PRIOR_MEAN_RATING, VOTE_CONFIDENCE);
        return SimilarityFunctions.clamp01(shrunk / 10d);
    }

    private double recencyScore(MovieFeatures movie, LocalDate today) {
        if (movie.releaseDate() == null) {
            return 0d;
        }
        long ageInDays = ChronoUnit.DAYS.between(movie.releaseDate(), today);
        return SimilarityFunctions.exponentialDecay(ageInDays, RECENCY_HALF_LIFE_DAYS);
    }

    @Override
    public RecommendationType type() {
        return RecommendationType.POPULARITY;
    }

    /** Always applicable - this is the universal fallback. */
    @Override
    public boolean supports(UserTasteProfile profile) {
        return true;
    }
}
