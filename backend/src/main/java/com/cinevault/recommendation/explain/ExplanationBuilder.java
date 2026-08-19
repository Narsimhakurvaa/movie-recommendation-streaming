package com.cinevault.recommendation.explain;

import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.SignalContribution;
import com.cinevault.recommendation.model.SignalContribution.SignalKind;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Turns the signal contributions recorded during scoring into a short,
 * human-readable sentence.
 *
 * <p>The important property is that explanations are <em>derived</em>: the
 * builder inspects the contributions that actually moved the score and renders
 * the strongest one. Nothing here invents a reason, and a film with no recorded
 * evidence falls back to a neutral, honest statement rather than a fabricated
 * claim.
 *
 * <p>Where two contributions of the same kind fired (for example two shared
 * genres) the phrasing merges them, because "Because you enjoy Sci-Fi and
 * Thriller" reads better than repeating the sentence twice.
 */
public final class ExplanationBuilder {

    /**
     * Ranking used to break ties when several signals have similar strength.
     * Specific, personal evidence ("you liked Interstellar") is more convincing
     * than generic evidence ("it is popular"), so it is preferred even when the
     * numeric contribution is marginally lower.
     */
    private static final Map<SignalKind, Integer> SPECIFICITY = Map.ofEntries(
            Map.entry(SignalKind.SIMILAR_TO_LIKED, 100),
            Map.entry(SignalKind.SHARED_DIRECTOR, 95),
            Map.entry(SignalKind.SIMILAR_USERS, 90),
            Map.entry(SignalKind.SHARED_CAST, 80),
            Map.entry(SignalKind.SHARED_WRITER, 75),
            Map.entry(SignalKind.FAVOURITE_GENRE, 70),
            Map.entry(SignalKind.ONBOARDING_PREFERENCE, 65),
            Map.entry(SignalKind.SHARED_KEYWORD, 60),
            Map.entry(SignalKind.SHARED_GENRE, 55),
            Map.entry(SignalKind.LANGUAGE_MATCH, 40),
            Map.entry(SignalKind.HIGHLY_RATED, 30),
            Map.entry(SignalKind.RECENT_RELEASE, 25),
            Map.entry(SignalKind.TRENDING, 20));

    /**
     * Signals that say something about <em>this user</em>. A personal reason is
     * always more useful than a generic one, so these are considered first.
     *
     * <p>Without this tiering, quality signals would dominate almost every
     * explanation: a Bayesian-shrunk rating lands around 0.8 for any well-liked
     * film, which numerically outweighs a genuine but modest 0.5 genre match.
     * The result would be a feed where nearly every card reads "Critically
     * acclaimed", which tells the user nothing about why it was chosen for them.
     */
    private static final java.util.Set<SignalKind> PERSONAL_SIGNALS = java.util.EnumSet.of(
            SignalKind.SIMILAR_TO_LIKED,
            SignalKind.SHARED_DIRECTOR,
            SignalKind.SHARED_WRITER,
            SignalKind.SHARED_CAST,
            SignalKind.SIMILAR_USERS,
            SignalKind.FAVOURITE_GENRE,
            SignalKind.ONBOARDING_PREFERENCE,
            SignalKind.SHARED_KEYWORD,
            SignalKind.SHARED_GENRE,
            SignalKind.LANGUAGE_MATCH);

    /**
     * A personal signal weaker than this is treated as noise, letting the
     * explanation fall back to an honest generic statement instead of claiming
     * a connection the arithmetic does not really support.
     */
    private static final double MIN_PERSONAL_STRENGTH = 0.05;

    private ExplanationBuilder() {
    }

    /**
     * Builds the explanation for a scored candidate.
     *
     * @param scored the candidate, carrying its recorded contributions
     * @param type   the strategy that produced it, used for the fallback wording
     * @return a sentence such as {@code "Because you liked Interstellar"}
     */
    public static String describe(ScoredMovie scored, RecommendationType type) {
        List<SignalContribution> signals = scored.contributions();
        if (signals.isEmpty()) {
            return fallback(type);
        }

        Comparator<SignalContribution> byRelevance = Comparator
                .comparingDouble((SignalContribution c) -> c.strength() * specificityBoost(c.kind()))
                .thenComparingInt(c -> SPECIFICITY.getOrDefault(c.kind(), 0));

        // Prefer a personal reason; only fall back to a generic one when no
        // personal signal was strong enough to be honest about.
        SignalContribution top = signals.stream()
                .filter(c -> PERSONAL_SIGNALS.contains(c.kind()))
                .filter(c -> c.strength() >= MIN_PERSONAL_STRENGTH)
                .max(byRelevance)
                .orElseGet(() -> signals.stream().max(byRelevance).orElseThrow());

        // Merge a second subject of the same kind for more natural phrasing.
        String secondary = signals.stream()
                .filter(c -> c.kind() == top.kind())
                .filter(c -> !c.subject().equals(top.subject()))
                .max(Comparator.comparingDouble(SignalContribution::strength))
                .map(SignalContribution::subject)
                .orElse(null);

        return render(top, secondary, scored);
    }

    /**
     * Specificity acts as a gentle multiplier rather than an override, so a
     * dominant generic signal can still win over a negligible specific one.
     */
    private static double specificityBoost(SignalKind kind) {
        return 1.0 + (SPECIFICITY.getOrDefault(kind, 0) / 100.0);
    }

    private static String render(SignalContribution c, String secondary, ScoredMovie scored) {
        String subject = c.subject();
        return switch (c.kind()) {
            case SIMILAR_TO_LIKED -> "Because you liked " + subject;
            case SHARED_DIRECTOR -> "Because you liked movies directed by " + subject;
            case SHARED_WRITER -> "Written by " + subject + ", whose work you have enjoyed";
            case SHARED_CAST -> secondary == null
                    ? "Starring " + subject + ", who appears in films you rated highly"
                    : "Starring " + subject + " and " + secondary;
            case SIMILAR_USERS -> "Popular among viewers with similar taste";
            case FAVOURITE_GENRE, SHARED_GENRE -> secondary == null
                    ? "Because you frequently watch " + subject
                    : "Because you frequently watch " + subject + " and " + secondary;
            case ONBOARDING_PREFERENCE -> secondary == null
                    ? "Matches your interest in " + subject
                    : "Matches your interest in " + subject + " and " + secondary;
            case SHARED_KEYWORD -> "Explores " + subject + ", a theme you keep coming back to";
            case LANGUAGE_MATCH -> "In " + subject + ", a language you watch often";
            case HIGHLY_RATED -> ratingSentence(scored);
            case RECENT_RELEASE -> "Recently released and highly rated";
            case TRENDING -> "Trending on CineVault right now";
        };
    }

    /** Uses the real aggregate so the number shown is never invented. */
    private static String ratingSentence(ScoredMovie scored) {
        double rating = scored.movie().externalRating();
        return String.format("Critically acclaimed, rated %.1f/10", rating);
    }

    private static String fallback(RecommendationType type) {
        return switch (type) {
            case COLD_START -> "A well-loved title to get you started";
            case POPULARITY -> "Popular with viewers right now";
            case SIMILAR -> "Shares themes with the title you are viewing";
            case COLLABORATIVE -> "Popular among viewers with similar taste";
            case CONTENT_BASED -> "Similar to titles in your history";
            case HYBRID -> "Recommended based on your activity";
        };
    }
}
