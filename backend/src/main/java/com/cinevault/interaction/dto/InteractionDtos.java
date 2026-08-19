package com.cinevault.interaction.dto;

import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request and response models for user interactions. */
public final class InteractionDtos {

    private InteractionDtos() {
    }

    // ------------------------------------------------------------ watchlist

    @Schema(name = "WatchlistItemResponse")
    public record WatchlistItemResponse(
            Long id,
            MovieSummary movie,
            String note,
            Instant addedAt) {
    }

    @Schema(name = "WatchlistStatus", description = "Whether a film is saved")
    public record WatchlistStatus(Long movieId, boolean saved) {
    }

    @Schema(name = "AddToWatchlistRequest")
    public record AddToWatchlistRequest(
            @Size(max = 255) String note) {
    }

    // -------------------------------------------------------------- ratings

    @Schema(name = "RatingRequest")
    public record RatingRequest(
            @NotNull
            @Min(value = 1, message = "Rating must be at least 1")
            @Max(value = 5, message = "Rating must be at most 5")
            @Schema(example = "4", description = "Score from 1 to 5")
            Integer score) {
    }

    @Schema(name = "RatingResponse")
    public record RatingResponse(
            Long movieId,
            int score,
            @Schema(description = "Recomputed platform average after this change")
            java.math.BigDecimal movieAverageRating,
            @Schema(description = "Recomputed number of platform ratings")
            int movieRatingCount,
            Instant updatedAt) {
    }

    // -------------------------------------------------------------- reviews

    @Schema(name = "ReviewRequest")
    public record ReviewRequest(
            @Size(max = 160) String title,

            @NotBlank(message = "Review body is required")
            @Size(min = 20, max = 5000,
                    message = "Review must be between 20 and 5000 characters")
            String body,

            Boolean containsSpoilers) {
    }

    @Schema(name = "ReviewResponse")
    public record ReviewResponse(
            Long id,
            Long movieId,
            String movieTitle,
            AuthorSummary author,
            String title,
            String body,
            boolean containsSpoilers,
            String status,
            @Schema(description = "The author's own rating, when they have also rated the film")
            Integer authorRating,
            @Schema(description = "True when the signed-in user wrote this review")
            boolean ownedByCurrentUser,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "AuthorSummary")
    public record AuthorSummary(Long id, String displayName, String avatarUrl) {
    }

    @Schema(name = "ModerateReviewRequest")
    public record ModerateReviewRequest(
            @NotBlank @Schema(allowableValues = {"PUBLISHED", "HIDDEN", "FLAGGED"})
            String status,
            @Size(max = 255) String moderationNote) {
    }

    // -------------------------------------------------------- watch history

    @Schema(name = "RecordInteractionRequest")
    public record RecordInteractionRequest(
            @NotBlank
            @Schema(allowableValues = {"VIEWED_DETAILS", "WATCHED_TRAILER", "STARTED_WATCHING",
                    "COMPLETED", "ADDED_TO_WATCHLIST", "RATED"})
            String interactionType,

            @Min(0) @Max(100)
            @Schema(description = "Playback progress, where applicable")
            Integer progressPercent) {
    }

    @Schema(name = "WatchHistoryResponse")
    public record WatchHistoryResponse(
            Long id,
            MovieSummary movie,
            String interactionType,
            Integer progressPercent,
            Instant occurredAt) {
    }
}
