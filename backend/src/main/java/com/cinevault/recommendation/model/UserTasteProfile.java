package com.cinevault.recommendation.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Aggregated view of a single user's taste, assembled once per recommendation
 * request and then shared by every strategy.
 *
 * <p>Building this up-front is what keeps the engine free of N+1 queries: the
 * service issues a small fixed number of aggregate queries, and the strategies
 * then operate purely in memory.
 *
 * <p>All affinity maps are normalised to {@code [0, 1]} by
 * {@link #normalised(Map)} so that weights in the hybrid blend remain
 * comparable regardless of how much history a user has.
 *
 * @param userId              the user being served
 * @param ratings             movieId to score on a 1-5 scale
 * @param genreAffinity       genreId to affinity in [0,1], derived from ratings and history
 * @param keywordAffinity     keywordId to affinity in [0,1]
 * @param castAffinity        personId to affinity in [0,1] for billed cast
 * @param directorAffinity    personId to affinity in [0,1] for directors
 * @param writerAffinity      personId to affinity in [0,1] for writers
 * @param languageAffinity    ISO-639-1 language to affinity in [0,1]
 * @param declaredGenreIds    genres the user explicitly picked during onboarding
 * @param interactedMovieIds  every movie the user has already engaged with
 * @param watchlistMovieIds   movies saved for later
 * @param likedMovieIds       movies rated at or above the "liked" threshold
 * @param preferredLanguages  languages explicitly selected in settings
 * @param minimumRating       minimum external rating the user will accept
 * @param includeAdult        whether adult titles may be recommended
 */
public record UserTasteProfile(
        long userId,
        Map<Long, Integer> ratings,
        Map<Long, Double> genreAffinity,
        Map<Long, Double> keywordAffinity,
        Map<Long, Double> castAffinity,
        Map<Long, Double> directorAffinity,
        Map<Long, Double> writerAffinity,
        Map<String, Double> languageAffinity,
        Set<Long> declaredGenreIds,
        Set<Long> interactedMovieIds,
        Set<Long> watchlistMovieIds,
        Set<Long> likedMovieIds,
        Set<String> preferredLanguages,
        double minimumRating,
        boolean includeAdult) {

    /** Ratings at or above this score are treated as a positive signal. */
    public static final int LIKED_THRESHOLD = 4;

    /**
     * Below this many rating signals a user is considered "cold" and the
     * collaborative strategy cannot say anything statistically meaningful.
     */
    public static final int COLD_START_RATING_THRESHOLD = 3;

    public UserTasteProfile {
        ratings = ratings == null ? Map.of() : Map.copyOf(ratings);
        genreAffinity = copyOf(genreAffinity);
        keywordAffinity = copyOf(keywordAffinity);
        castAffinity = copyOf(castAffinity);
        directorAffinity = copyOf(directorAffinity);
        writerAffinity = copyOf(writerAffinity);
        languageAffinity = languageAffinity == null ? Map.of() : Map.copyOf(languageAffinity);
        declaredGenreIds = copySet(declaredGenreIds);
        interactedMovieIds = copySet(interactedMovieIds);
        watchlistMovieIds = copySet(watchlistMovieIds);
        likedMovieIds = copySet(likedMovieIds);
        preferredLanguages = preferredLanguages == null
                ? Collections.emptySet() : Set.copyOf(preferredLanguages);
    }

    private static <K> Map<K, Double> copyOf(Map<K, Double> in) {
        return in == null ? Map.of() : Map.copyOf(in);
    }

    private static <T> Set<T> copySet(Set<T> in) {
        return in == null ? Collections.emptySet() : Set.copyOf(in);
    }

    /**
     * True when there is not enough interaction data to personalise reliably,
     * which routes the request to the cold-start strategy.
     */
    public boolean isColdStart() {
        return ratings.size() < COLD_START_RATING_THRESHOLD && interactedMovieIds.size() < 5;
    }

    /** True when the user has given us nothing at all, not even onboarding picks. */
    public boolean hasNoSignals() {
        return ratings.isEmpty() && interactedMovieIds.isEmpty() && declaredGenreIds.isEmpty();
    }

    /**
     * Scales a raw affinity tally into {@code [0, 1]} by dividing through by the
     * largest entry. Using the max (rather than the sum) preserves the relative
     * ordering while guaranteeing the strongest signal always reaches 1.0, which
     * keeps the hybrid weights meaningful across users with very different
     * amounts of history.
     */
    public static <K> Map<K, Double> normalised(Map<K, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d);
        if (max <= 0d) {
            return Map.of();
        }
        return raw.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> e.getValue() / max));
    }

    /** An empty profile for an anonymous or brand-new visitor. */
    public static UserTasteProfile empty(long userId) {
        return new UserTasteProfile(userId, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0d, false);
    }
}
