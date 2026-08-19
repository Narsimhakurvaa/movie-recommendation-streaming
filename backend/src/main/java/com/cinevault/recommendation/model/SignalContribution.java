package com.cinevault.recommendation.model;

/**
 * A single piece of evidence for why a film was recommended.
 *
 * <p>Strategies emit these as a by-product of scoring, so explanations are
 * always grounded in the arithmetic that actually produced the ranking rather
 * than being generated after the fact.
 *
 * @param kind      which signal fired
 * @param strength  contribution in {@code [0, 1]}; higher wins when the
 *                  explanation builder picks the headline reason
 * @param subject   human-readable subject, e.g. a genre or director name
 * @param referenceId optional identifier of the underlying entity
 */
public record SignalContribution(
        SignalKind kind,
        double strength,
        String subject,
        Long referenceId) {

    public static SignalContribution of(SignalKind kind, double strength, String subject) {
        return new SignalContribution(kind, strength, subject, null);
    }

    public static SignalContribution of(SignalKind kind, double strength, String subject, Long referenceId) {
        return new SignalContribution(kind, strength, subject, referenceId);
    }

    /** The categories of evidence the engine can produce. */
    public enum SignalKind {
        SHARED_GENRE,
        FAVOURITE_GENRE,
        SHARED_KEYWORD,
        SHARED_DIRECTOR,
        SHARED_CAST,
        SHARED_WRITER,
        SIMILAR_TO_LIKED,
        SIMILAR_USERS,
        LANGUAGE_MATCH,
        HIGHLY_RATED,
        TRENDING,
        RECENT_RELEASE,
        ONBOARDING_PREFERENCE
    }
}
