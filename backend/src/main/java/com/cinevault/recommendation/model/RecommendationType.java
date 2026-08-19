package com.cinevault.recommendation.model;

/** Identifies which strategy produced a recommendation. */
public enum RecommendationType {

    /** Weighted blend of every available signal. */
    HYBRID,
    /** Driven by metadata similarity to titles the user already liked. */
    CONTENT_BASED,
    /** Driven by the behaviour of users with overlapping taste. */
    COLLABORATIVE,
    /** Driven by global popularity and rating quality. */
    POPULARITY,
    /** Serves users with too little history to personalise. */
    COLD_START,
    /** "More like this" for a specific seed film. */
    SIMILAR
}
