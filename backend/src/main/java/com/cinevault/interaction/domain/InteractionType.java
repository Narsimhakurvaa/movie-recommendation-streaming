package com.cinevault.interaction.domain;

/**
 * The kinds of engagement tracked in watch history.
 *
 * <p>{@link #weight()} expresses how much each action says about genuine
 * interest, and feeds directly into the recommendation engine's affinity
 * calculations. Completing a film is a far stronger endorsement than opening
 * its detail page, and the weights encode that ordering explicitly rather than
 * leaving it implicit in scattered service code.
 */
public enum InteractionType {

    /** Opened the detail page. Weak: could be idle browsing. */
    VIEWED_DETAILS(0.15),
    /** Played the trailer. Mild intent. */
    WATCHED_TRAILER(0.30),
    /** Began watching. Clear intent. */
    STARTED_WATCHING(0.60),
    /** Watched to the end. The strongest implicit signal available. */
    COMPLETED(1.00),
    /** Saved for later. Explicit intent, though not yet consumption. */
    ADDED_TO_WATCHLIST(0.50),
    /** Submitted a score. Explicit, but the score itself carries the direction. */
    RATED(0.70);

    private final double weight;

    InteractionType(double weight) {
        this.weight = weight;
    }

    /** Relative strength of this signal, in {@code (0, 1]}. */
    public double weight() {
        return weight;
    }
}
