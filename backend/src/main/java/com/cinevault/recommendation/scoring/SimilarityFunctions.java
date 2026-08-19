package com.cinevault.recommendation.scoring;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Similarity and decay primitives shared by the scoring strategies.
 *
 * <p>Kept separate from the strategies so each metric can be reasoned about and
 * tested in isolation, and so the choice of metric is an explicit, documented
 * decision rather than an implementation detail buried in a loop.
 */
public final class SimilarityFunctions {

    private SimilarityFunctions() {
    }

    /**
     * Jaccard index: {@code |A n B| / |A u B|}.
     *
     * <p>Chosen for genre overlap because it penalises breadth. A sprawling
     * eight-genre film should not look highly similar to a focused two-genre
     * film merely because it happens to contain both of those genres.
     *
     * @return a value in {@code [0, 1]}; zero when either set is empty
     */
    public static double jaccard(Set<Long> a, Set<Long> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        long intersection = a.stream().filter(b::contains).count();
        if (intersection == 0) {
            return 0d;
        }
        int union = a.size() + b.size() - (int) intersection;
        return (double) intersection / union;
    }

    /**
     * Cosine similarity between two sparse vectors held as maps.
     *
     * <p>Used for user-to-user rating comparison because, unlike Jaccard, it
     * accounts for rating <em>magnitude</em>: two users who both rated the same
     * five films are only similar if they also agreed on those films.
     *
     * @return a value in {@code [0, 1]} for non-negative inputs
     */
    public static double cosine(Map<Long, Double> a, Map<Long, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        // Iterate the smaller map; the dot product only spans shared keys.
        Map<Long, Double> smaller = a.size() <= b.size() ? a : b;
        Map<Long, Double> larger = smaller == a ? b : a;

        double dot = 0d;
        for (Map.Entry<Long, Double> e : smaller.entrySet()) {
            Double other = larger.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        if (dot == 0d) {
            return 0d;
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        return (normA == 0d || normB == 0d) ? 0d : dot / (normA * normB);
    }

    /**
     * Mean affinity across a film's attributes, weighted by how strongly the
     * user is attached to each one.
     *
     * <p>Averaging over the film's own attribute count (rather than summing)
     * prevents films with long cast lists from dominating purely by volume.
     *
     * @param attributeIds the film's attributes, e.g. its cast identifiers
     * @param affinity     the user's affinity per attribute, in {@code [0, 1]}
     * @return a value in {@code [0, 1]}
     */
    public static double meanAffinity(Collection<Long> attributeIds, Map<Long, Double> affinity) {
        if (attributeIds.isEmpty() || affinity.isEmpty()) {
            return 0d;
        }
        double total = 0d;
        for (Long id : attributeIds) {
            total += affinity.getOrDefault(id, 0d);
        }
        return total / attributeIds.size();
    }

    /**
     * The single strongest affinity among a film's attributes.
     *
     * <p>Preferred over the mean for directors and writers: a film by a
     * favourite director is a strong signal even when the rest of the crew is
     * unknown to the user, and averaging would dilute that away.
     */
    public static double peakAffinity(Collection<Long> attributeIds, Map<Long, Double> affinity) {
        double peak = 0d;
        for (Long id : attributeIds) {
            peak = Math.max(peak, affinity.getOrDefault(id, 0d));
        }
        return peak;
    }

    /**
     * Exponential recency decay with a configurable half-life.
     *
     * <p>Exponential rather than linear so that the difference between a film
     * from this year and last year is meaningful, while the difference between
     * 1980 and 1985 is negligible, which matches how viewers actually perceive
     * "new".
     *
     * @param ageInDays      age of the film in days; negative values (unreleased)
     *                       are clamped to zero
     * @param halfLifeInDays days after which the score halves
     * @return a value in {@code (0, 1]}
     */
    public static double exponentialDecay(double ageInDays, double halfLifeInDays) {
        if (halfLifeInDays <= 0) {
            throw new IllegalArgumentException("halfLifeInDays must be positive");
        }
        double age = Math.max(0d, ageInDays);
        return Math.pow(0.5d, age / halfLifeInDays);
    }

    /**
     * Bayesian (shrunk) average rating, the same technique IMDb uses for its
     * Top 250.
     *
     * <p>A film with a single 10/10 vote must not outrank a film with a 8.5
     * average from thirty thousand votes. Blending the observed mean towards a
     * global prior, in proportion to how few votes it has, encodes exactly that.
     *
     * @param rating        observed mean rating
     * @param votes         number of votes behind it
     * @param priorMean     global mean rating across the catalogue
     * @param confidence    votes required before the observed mean dominates
     * @return the shrunk rating on the same scale as {@code rating}
     */
    public static double bayesianAverage(double rating, int votes, double priorMean, double confidence) {
        if (votes <= 0) {
            return priorMean;
        }
        return ((votes * rating) + (confidence * priorMean)) / (votes + confidence);
    }

    /**
     * Compresses an unbounded positive quantity into {@code [0, 1)}.
     *
     * <p>Provider popularity indices have no fixed upper bound and are heavily
     * skewed, so a logarithmic squash is applied before the value can be mixed
     * with the other normalised signals.
     */
    public static double logNormalise(double value, double scale) {
        if (value <= 0) {
            return 0d;
        }
        return Math.log1p(value) / Math.log1p(scale + value);
    }

    /** Clamps a value into the inclusive range {@code [0, 1]}. */
    public static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }
}
