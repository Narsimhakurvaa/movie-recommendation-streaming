package com.cinevault.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Administrative dashboard and management payloads. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Headline metrics for the dashboard. */
    @Schema(name = "DashboardStatistics")
    public record DashboardStatistics(
            UserStatistics users,
            CatalogueStatistics catalogue,
            EngagementStatistics engagement,
            List<PopularMovie> mostPopular,
            List<ActiveUser> mostActive,
            @Schema(description = "How many recommendations each strategy has served")
            Map<String, Long> recommendationsByType,
            Instant generatedAt) {
    }

    @Schema(name = "UserStatistics")
    public record UserStatistics(long total, long enabled, long disabled, long joinedLast30Days) {
    }

    @Schema(name = "CatalogueStatistics")
    public record CatalogueStatistics(long movies, long genres, long people,
                                      long releasedLast12Months) {
    }

    @Schema(name = "EngagementStatistics")
    public record EngagementStatistics(long ratings, long reviews, long hiddenReviews,
                                       long watchlistEntries, long interactionsLast7Days,
                                       long recommendationsLast7Days) {
    }

    @Schema(name = "PopularMovie")
    public record PopularMovie(Long id, String title, long interactions,
                               java.math.BigDecimal averageRating, int ratingCount) {
    }

    @Schema(name = "ActiveUser")
    public record ActiveUser(Long id, String displayName, String email, long interactions) {
    }

    @Schema(name = "AdminUserResponse")
    public record AdminUserResponse(
            Long id,
            String email,
            String displayName,
            boolean enabled,
            boolean emailVerified,
            List<String> roles,
            long ratingCount,
            long reviewCount,
            Instant createdAt,
            Instant lastLoginAt) {
    }

    @Schema(name = "SetUserEnabledRequest")
    public record SetUserEnabledRequest(
            @NotNull Boolean enabled,
            String reason) {
    }

    /** Outcome of a catalogue synchronisation run. */
    @Schema(name = "SyncResult")
    public record SyncResult(
            String provider,
            int fetched,
            int created,
            int updated,
            int skipped,
            List<String> warnings,
            Instant completedAt) {
    }
}
