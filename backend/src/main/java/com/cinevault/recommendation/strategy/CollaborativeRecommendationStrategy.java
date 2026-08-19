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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User-based collaborative filtering: finds viewers whose ratings correlate
 * with the target user's, then recommends what those neighbours rated highly
 * and the target user has not yet seen.
 *
 * <h2>Why user-based, and why mean-centred</h2>
 * <p>Raw rating vectors are compared after subtracting each user's own mean.
 * Without that correction a generous rater who scores everything 4-5 looks
 * similar to every other generous rater regardless of taste. Mean-centring
 * compares the <em>shape</em> of opinion (what someone liked relative to their
 * own baseline), which is the signal that actually matters.
 *
 * <p>Neighbour contributions are weighted by similarity and divided by the sum
 * of similarities, producing a predicted deviation that is then mapped back
 * into {@code [0, 1]}. A neighbour who agrees strongly therefore counts for
 * more than several weakly-correlated ones.
 *
 * <h2>Confidence weighting</h2>
 * <p>A prediction backed by one neighbour is far less trustworthy than one
 * backed by twenty. The final score is damped by a confidence factor that grows
 * with the number of contributing neighbours, preventing a single enthusiastic
 * stranger from dominating the results.
 */
public class CollaborativeRecommendationStrategy implements RecommendationStrategy {

    /** Neighbours below this similarity contribute noise rather than signal. */
    static final double MIN_SIMILARITY = 0.05;

    /** Only the closest neighbours are used; distant ones dilute the signal. */
    static final int MAX_NEIGHBOURS = 50;

    /** Neighbour count at which the prediction is considered fully confident. */
    static final double CONFIDENCE_SATURATION = 8.0;

    private final NeighbourRatingSource ratingSource;

    public CollaborativeRecommendationStrategy(NeighbourRatingSource ratingSource) {
        this.ratingSource = ratingSource;
    }

    /**
     * Supplies the rating vectors of other users.
     *
     * <p>An interface rather than a repository dependency so the strategy stays
     * persistence-agnostic and directly unit-testable. Implementations are
     * expected to return only users who share at least one rated film with the
     * target, which the database can determine far more cheaply than Java can.
     */
    @FunctionalInterface
    public interface NeighbourRatingSource {
        /**
         * @param userId the user to exclude (the target themselves)
         * @return userId to (movieId to score) for candidate neighbours
         */
        Map<Long, Map<Long, Integer>> neighbourRatings(long userId);
    }

    @Override
    public List<ScoredMovie> score(UserTasteProfile profile, Collection<MovieFeatures> candidates) {
        Map<Long, Integer> mine = profile.ratings();
        if (mine.size() < UserTasteProfile.COLD_START_RATING_THRESHOLD) {
            return List.of();
        }

        Map<Long, Map<Long, Integer>> neighbours = ratingSource.neighbourRatings(profile.userId());
        if (neighbours == null || neighbours.isEmpty()) {
            return List.of();
        }

        double myMean = mean(mine.values());
        Map<Long, Double> myVector = centre(mine, myMean);

        // A user who gave every film the same score mean-centres to the zero
        // vector, for which cosine similarity is undefined. They genuinely
        // express no relative preference, so no neighbour can be inferred and
        // the hybrid blend will fall back to its other components.
        if (isZeroVector(myVector)) {
            return List.of();
        }

        // Rank neighbours by mean-centred cosine similarity.
        List<Neighbour> ranked = new ArrayList<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : neighbours.entrySet()) {
            Map<Long, Integer> theirs = entry.getValue();
            if (theirs == null || theirs.isEmpty()) {
                continue;
            }
            double theirMean = mean(theirs.values());
            Map<Long, Double> theirVector = centre(theirs, theirMean);
            if (isZeroVector(theirVector)) {
                continue; // flat rater: carries no comparable preference signal
            }
            double similarity = SimilarityFunctions.cosine(myVector, theirVector);
            if (similarity >= MIN_SIMILARITY) {
                ranked.add(new Neighbour(entry.getKey(), similarity, theirs, theirMean));
            }
        }
        if (ranked.isEmpty()) {
            return List.of();
        }
        ranked.sort(Comparator.comparingDouble(Neighbour::similarity).reversed());
        if (ranked.size() > MAX_NEIGHBOURS) {
            ranked = ranked.subList(0, MAX_NEIGHBOURS);
        }

        // Accumulate weighted deviations for every candidate the user has not seen.
        Map<Long, Accumulator> accumulators = new HashMap<>();
        for (Neighbour n : ranked) {
            for (Map.Entry<Long, Integer> rated : n.ratings().entrySet()) {
                long movieId = rated.getKey();
                if (mine.containsKey(movieId)) {
                    continue; // already rated by the target user
                }
                double deviation = rated.getValue() - n.mean();
                accumulators.computeIfAbsent(movieId, id -> new Accumulator())
                        .add(n.similarity(), deviation);
            }
        }

        List<ScoredMovie> results = new ArrayList<>();
        for (MovieFeatures movie : candidates) {
            Accumulator acc = accumulators.get(movie.movieId());
            if (acc == null || acc.similaritySum <= 0) {
                continue;
            }
            double predictedDeviation = acc.weightedDeviation / acc.similaritySum;
            // Map a deviation on the 1-5 scale (range +-4) into [0,1] around 0.5.
            double normalised = SimilarityFunctions.clamp01(0.5d + (predictedDeviation / 8.0d));

            // Damp by how many neighbours actually backed this prediction.
            double confidence = acc.count / (acc.count + CONFIDENCE_SATURATION);
            double finalScore = normalised * confidence;
            if (finalScore <= 0) {
                continue;
            }

            ScoredMovie scored = new ScoredMovie(movie, finalScore);
            scored.addContribution(SignalContribution.of(
                    SignalKind.SIMILAR_USERS, finalScore, "viewers with similar taste"));
            results.add(scored);
        }
        return results;
    }

    private static double mean(Collection<Integer> scores) {
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0d);
    }

    /** Subtracts the user's own average so ratings express relative preference. */
    private static Map<Long, Double> centre(Map<Long, Integer> ratings, double mean) {
        Map<Long, Double> centred = new HashMap<>(ratings.size());
        ratings.forEach((movieId, score) -> centred.put(movieId, score - mean));
        return centred;
    }

    /** True when every component is (near) zero, i.e. the user rated everything alike. */
    private static boolean isZeroVector(Map<Long, Double> vector) {
        return vector.values().stream().allMatch(v -> Math.abs(v) < 1e-9);
    }

    private record Neighbour(long userId, double similarity,
                             Map<Long, Integer> ratings, double mean) {
    }

    private static final class Accumulator {
        private double weightedDeviation;
        private double similaritySum;
        private int count;

        void add(double similarity, double deviation) {
            weightedDeviation += similarity * deviation;
            similaritySum += similarity;
            count++;
        }
    }

    @Override
    public RecommendationType type() {
        return RecommendationType.COLLABORATIVE;
    }

    @Override
    public boolean supports(UserTasteProfile profile) {
        return profile.ratings().size() >= UserTasteProfile.COLD_START_RATING_THRESHOLD;
    }
}
