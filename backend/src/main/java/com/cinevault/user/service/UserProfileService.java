package com.cinevault.user.service;

import com.cinevault.catalogue.repository.GenreRepository;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.UserFavouriteGenre;
import com.cinevault.interaction.domain.UserPreferences;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.interaction.repository.ReviewRepository;
import com.cinevault.interaction.repository.UserFavouriteGenreRepository;
import com.cinevault.interaction.repository.UserPreferencesRepository;
import com.cinevault.interaction.repository.WatchHistoryRepository;
import com.cinevault.interaction.repository.WatchlistRepository;
import com.cinevault.user.domain.Role;
import com.cinevault.user.domain.User;
import com.cinevault.user.dto.ProfileDtos.ActivitySummary;
import com.cinevault.user.dto.ProfileDtos.FavouriteGenre;
import com.cinevault.user.dto.ProfileDtos.PreferencesResponse;
import com.cinevault.user.dto.ProfileDtos.UpdatePreferencesRequest;
import com.cinevault.user.dto.ProfileDtos.UpdateProfileRequest;
import com.cinevault.user.dto.ProfileDtos.UserProfileResponse;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reading and updating a user's profile and preferences.
 *
 * <p>Preferences are more than cosmetic: minimum rating, language and adult
 * settings are applied as hard filters when generating recommendations, and
 * favourite genres feed the cold-start strategy.
 */
@Service
@Transactional(readOnly = true)
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    /** Cap on stored onboarding genres; beyond this the signal stops meaning anything. */
    private static final int MAX_FAVOURITE_GENRES = 8;

    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final UserFavouriteGenreRepository favouriteGenreRepository;
    private final GenreRepository genreRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchHistoryRepository watchHistoryRepository;

    public UserProfileService(UserRepository userRepository,
                              UserPreferencesRepository preferencesRepository,
                              UserFavouriteGenreRepository favouriteGenreRepository,
                              GenreRepository genreRepository,
                              RatingRepository ratingRepository,
                              ReviewRepository reviewRepository,
                              WatchlistRepository watchlistRepository,
                              WatchHistoryRepository watchHistoryRepository) {
        this.userRepository = userRepository;
        this.preferencesRepository = preferencesRepository;
        this.favouriteGenreRepository = favouriteGenreRepository;
        this.genreRepository = genreRepository;
        this.ratingRepository = ratingRepository;
        this.reviewRepository = reviewRepository;
        this.watchlistRepository = watchlistRepository;
        this.watchHistoryRepository = watchHistoryRepository;
    }

    public UserProfileResponse findProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        if (request.biography() != null) {
            user.setBiography(request.biography().isBlank() ? null : request.biography().trim());
        }
        log.debug("Updated profile for user {}", userId);
        return toResponse(userRepository.save(user));
    }

    /**
     * Replaces preferences and, when supplied, the favourite-genre selection.
     *
     * <p>Genres are replaced wholesale rather than merged: the UI presents them
     * as a single multi-select, so a partial update would make deselection
     * impossible.
     */
    @Transactional
    public UserProfileResponse updatePreferences(Long userId, UpdatePreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> new UserPreferences(user));

        if (request.preferredLanguages() != null) {
            preferences.setPreferredLanguageCodes(request.preferredLanguages());
        }
        if (request.includeAdult() != null) {
            preferences.setIncludeAdult(request.includeAdult());
        }
        if (request.minimumRating() != null) {
            preferences.setMinimumRating(request.minimumRating());
        }
        if (request.preferredDecadeFrom() != null) {
            preferences.setPreferredDecadeFrom(request.preferredDecadeFrom());
        }
        if (request.preferredDecadeTo() != null) {
            preferences.setPreferredDecadeTo(request.preferredDecadeTo());
        }
        if (request.theme() != null && !request.theme().isBlank()) {
            preferences.setTheme(request.theme());
        }
        if (request.emailNotifications() != null) {
            preferences.setEmailNotifications(request.emailNotifications());
        }
        preferences.touch();
        preferencesRepository.save(preferences);

        if (request.favouriteGenreSlugs() != null) {
            replaceFavouriteGenres(user, request.favouriteGenreSlugs());
            user.setOnboardingCompleted(true);
            userRepository.save(user);
        }
        log.debug("Updated preferences for user {}", userId);
        return toResponse(user);
    }

    private void replaceFavouriteGenres(User user, List<String> slugs) {
        favouriteGenreRepository.deleteByUserId(user.getId());
        // Flush the deletes before inserting, or the new rows can collide with
        // the old ones on the composite primary key.
        favouriteGenreRepository.flush();

        int index = 0;
        for (String slug : slugs.stream().distinct().limit(MAX_FAVOURITE_GENRES).toList()) {
            var genre = genreRepository.findBySlug(slug);
            if (genre.isEmpty()) {
                log.debug("Ignoring unknown genre slug: {}", slug);
                continue;
            }
            // Earlier selections weigh more, floored so later picks still count.
            BigDecimal weight = BigDecimal.valueOf(Math.max(0.4, 1.0 - (index * 0.1)))
                    .setScale(3, RoundingMode.HALF_UP);
            favouriteGenreRepository.save(new UserFavouriteGenre(user, genre.get(), weight));
            index++;
        }
    }

    private UserProfileResponse toResponse(User user) {
        Long userId = user.getId();

        var activity = new ActivitySummary(
                ratingRepository.countByUserId(userId),
                reviewRepository.countByUserId(userId),
                watchlistRepository.countByUserId(userId),
                watchHistoryRepository.countByUserId(userId));

        var preferences = preferencesRepository.findByUserId(userId)
                .map(p -> new PreferencesResponse(p.preferredLanguageCodes(), p.isIncludeAdult(),
                        p.getMinimumRating(), p.getPreferredDecadeFrom(), p.getPreferredDecadeTo(),
                        p.getTheme(), p.isEmailNotifications()))
                .orElseGet(() -> new PreferencesResponse(List.of(), false, BigDecimal.ZERO,
                        null, null, "system", true));

        var favourites = favouriteGenreRepository.findByUserId(userId).stream()
                .map(f -> new FavouriteGenre(f.getGenre().getId(), f.getGenre().getName(),
                        f.getGenre().getSlug(), f.getWeight()))
                .toList();

        return new UserProfileResponse(userId, user.getEmail(), user.getDisplayName(),
                user.getAvatarUrl(), user.getBiography(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toUnmodifiableSet()),
                user.isEmailVerified(), user.isOnboardingCompleted(),
                user.getCreatedAt(), user.getLastLoginAt(),
                activity, preferences, favourites);
    }
}
