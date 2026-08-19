package com.cinevault.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Profile, preferences and activity-summary payloads. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    @Schema(name = "UserProfileResponse")
    public record UserProfileResponse(
            Long id,
            String email,
            String displayName,
            String avatarUrl,
            String biography,
            Set<String> roles,
            boolean emailVerified,
            boolean onboardingCompleted,
            Instant createdAt,
            Instant lastLoginAt,
            ActivitySummary activity,
            PreferencesResponse preferences,
            List<FavouriteGenre> favouriteGenres) {
    }

    /** Counts shown on the profile page. */
    @Schema(name = "ActivitySummary")
    public record ActivitySummary(
            long ratingCount,
            long reviewCount,
            long watchlistCount,
            long historyCount) {
    }

    @Schema(name = "FavouriteGenre")
    public record FavouriteGenre(Long id, String name, String slug, BigDecimal weight) {
    }

    @Schema(name = "UpdateProfileRequest")
    public record UpdateProfileRequest(
            @Size(min = 2, max = 80) String displayName,
            @Size(max = 512) @Pattern(regexp = "^$|^https?://.*",
                    message = "Avatar must be an http(s) URL") String avatarUrl,
            @Size(max = 500) String biography) {
    }

    @Schema(name = "PreferencesResponse")
    public record PreferencesResponse(
            List<String> preferredLanguages,
            boolean includeAdult,
            BigDecimal minimumRating,
            Short preferredDecadeFrom,
            Short preferredDecadeTo,
            String theme,
            boolean emailNotifications) {
    }

    @Schema(name = "UpdatePreferencesRequest")
    public record UpdatePreferencesRequest(
            List<String> preferredLanguages,
            Boolean includeAdult,
            @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal minimumRating,
            @Min(1870) @Max(2200) Short preferredDecadeFrom,
            @Min(1870) @Max(2200) Short preferredDecadeTo,
            @Pattern(regexp = "light|dark|system",
                    message = "Theme must be light, dark or system") String theme,
            Boolean emailNotifications,
            @Schema(description = "Genre slugs; replaces the existing selection")
            List<String> favouriteGenreSlugs) {
    }
}
