package com.cinevault.recommendation.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A candidate film together with its score and the evidence behind it.
 *
 * <p>Mutable by design: the hybrid strategy accumulates contributions from each
 * component strategy into a single instance rather than allocating a new object
 * per signal, which matters when scoring a few thousand candidates per request.
 * Instances never escape the engine; the service maps them to DTOs.
 */
public final class ScoredMovie {

    private final MovieFeatures movie;
    private final List<SignalContribution> contributions = new ArrayList<>();
    private double score;

    public ScoredMovie(MovieFeatures movie) {
        this.movie = movie;
    }

    public ScoredMovie(MovieFeatures movie, double score) {
        this.movie = movie;
        this.score = score;
    }

    public MovieFeatures movie() {
        return movie;
    }

    public long movieId() {
        return movie.movieId();
    }

    public double score() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void addScore(double delta) {
        this.score += delta;
    }

    public List<SignalContribution> contributions() {
        return contributions;
    }

    public void addContribution(SignalContribution contribution) {
        if (contribution != null && contribution.strength() > 0) {
            contributions.add(contribution);
        }
    }

    public void addContributions(List<SignalContribution> extra) {
        if (extra != null) {
            extra.forEach(this::addContribution);
        }
    }

    /** Highest score first; ties broken by popularity so ordering is stable. */
    public static Comparator<ScoredMovie> byScoreDescending() {
        return Comparator.comparingDouble(ScoredMovie::score).reversed()
                .thenComparing(s -> s.movie().popularity(), Comparator.reverseOrder())
                .thenComparingLong(ScoredMovie::movieId);
    }

    @Override
    public String toString() {
        return "ScoredMovie[" + movie.title() + " score=" + String.format("%.4f", score) + "]";
    }
}
