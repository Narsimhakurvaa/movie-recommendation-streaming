package com.cinevault.interaction.domain;

/** Moderation state of a review; mirrors the DB check constraint. */
public enum ReviewStatus {
    /** Visible to everyone. */
    PUBLISHED,
    /** Hidden by an administrator. */
    HIDDEN,
    /** Reported and awaiting moderator attention, still visible. */
    FLAGGED
}
