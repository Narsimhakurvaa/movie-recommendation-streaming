package com.cinevault.recommendation.dto;

import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Response models for the recommendation API. */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /**
     * A single recommendation.
     *
     * <p>The score is exposed rounded to three decimals so clients can sort or
     * display confidence, but the individual component scores and weights are
     * deliberately withheld: they are internal tuning details, and publishing
     * them would both leak the ranking model and let it be gamed.
     *
     * @param movie              the recommended film
     * @param score              final blended score in {@code [0, 1]}
     * @param reason             human-readable justification, derived from real signals
     * @param recommendationType which strategy produced it
     */
    @Schema(name = "RecommendationItem")
    public record RecommendationItem(
            MovieSummary movie,
            @Schema(example = "0.874") double score,
            @Schema(example = "Because you liked Interstellar") String reason,
            @Schema(example = "HYBRID") String recommendationType) {
    }

    /**
     * A titled group of recommendations, used to build the home page rails.
     *
     * @param key   stable identifier, e.g. {@code because-you-watched}
     * @param title display heading
     * @param subtitle optional supporting line
     * @param items the recommendations
     */
    @Schema(name = "RecommendationSection")
    public record RecommendationSection(
            String key,
            String title,
            String subtitle,
            List<RecommendationItem> items) {
    }

    /** The complete personalised home page payload. */
    @Schema(name = "HomeFeed")
    public record HomeFeed(
            @Schema(description = "Single hero title, chosen as the strongest recommendation")
            RecommendationItem hero,
            List<RecommendationSection> sections,
            @Schema(description = "True when the user has too little history to personalise")
            boolean coldStart,
            Instant generatedAt) {
    }

    /** One previously served recommendation, for the history page. */
    @Schema(name = "RecommendationHistoryItem")
    public record RecommendationHistoryItem(
            MovieSummary movie,
            double score,
            String reason,
            String recommendationType,
            Instant generatedAt) {
    }
}
