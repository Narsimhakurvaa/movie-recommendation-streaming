package com.cinevault.admin.service;

import com.cinevault.admin.dto.AdminDtos.ActiveUser;
import com.cinevault.admin.dto.AdminDtos.AdminUserResponse;
import com.cinevault.admin.dto.AdminDtos.CatalogueStatistics;
import com.cinevault.admin.dto.AdminDtos.DashboardStatistics;
import com.cinevault.admin.dto.AdminDtos.EngagementStatistics;
import com.cinevault.admin.dto.AdminDtos.PopularMovie;
import com.cinevault.admin.dto.AdminDtos.UserStatistics;
import com.cinevault.admin.repository.AdminStatisticsRepository;
import com.cinevault.catalogue.repository.GenreRepository;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.catalogue.repository.PersonRepository;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.exception.BadRequestException;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.ReviewStatus;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.interaction.repository.RecommendationLogRepository;
import com.cinevault.interaction.repository.ReviewRepository;
import com.cinevault.interaction.repository.WatchHistoryRepository;
import com.cinevault.interaction.repository.WatchlistRepository;
import com.cinevault.user.domain.Role;
import com.cinevault.user.domain.User;
import com.cinevault.user.repository.RefreshTokenRepository;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Administrative reporting and user management.
 *
 * <p>Every method here is reachable only through {@code /api/admin/**}, which
 * the security configuration restricts to {@code ROLE_ADMIN}, and the
 * controller additionally annotates each handler. Authorisation is enforced
 * server-side; the frontend's role check is a convenience, never the control.
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final int LEADERBOARD_SIZE = 10;

    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final PersonRepository personRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final WatchlistRepository watchlistRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminStatisticsRepository statisticsRepository;

    public AdminService(UserRepository userRepository,
                        MovieRepository movieRepository,
                        GenreRepository genreRepository,
                        PersonRepository personRepository,
                        RatingRepository ratingRepository,
                        ReviewRepository reviewRepository,
                        WatchlistRepository watchlistRepository,
                        WatchHistoryRepository watchHistoryRepository,
                        RecommendationLogRepository recommendationLogRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        AdminStatisticsRepository statisticsRepository) {
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
        this.personRepository = personRepository;
        this.ratingRepository = ratingRepository;
        this.reviewRepository = reviewRepository;
        this.watchlistRepository = watchlistRepository;
        this.watchHistoryRepository = watchHistoryRepository;
        this.recommendationLogRepository = recommendationLogRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.statisticsRepository = statisticsRepository;
    }

    /** Aggregated dashboard metrics. */
    public DashboardStatistics dashboard() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long totalUsers = userRepository.count();
        long enabledUsers = userRepository.countByEnabledTrue();

        var users = new UserStatistics(totalUsers, enabledUsers, totalUsers - enabledUsers,
                userRepository.countByCreatedAtAfter(thirtyDaysAgo));

        var catalogue = new CatalogueStatistics(movieRepository.count(), genreRepository.count(),
                personRepository.count(),
                movieRepository.countReleasedSince(LocalDate.now().minusMonths(12)));

        var engagement = new EngagementStatistics(ratingRepository.count(),
                reviewRepository.count(),
                reviewRepository.countByStatus(ReviewStatus.HIDDEN),
                watchlistRepository.count(),
                watchHistoryRepository.countByOccurredAtAfter(sevenDaysAgo),
                recommendationLogRepository.countByGeneratedAtAfter(sevenDaysAgo));

        var popular = statisticsRepository.findMostInteractedMovies(LEADERBOARD_SIZE).stream()
                .map(row -> new PopularMovie(row.getId(), row.getTitle(), row.getInteractions(),
                        row.getAverageRating(), row.getRatingCount()))
                .toList();

        var active = statisticsRepository.findMostActiveUsers(LEADERBOARD_SIZE).stream()
                .map(row -> new ActiveUser(row.getId(), row.getDisplayName(),
                        row.getEmail(), row.getInteractions()))
                .toList();

        Map<String, Long> byType = new LinkedHashMap<>();
        recommendationLogRepository.countByType()
                .forEach(row -> byType.put(row.getType(), row.getTotal()));

        return new DashboardStatistics(users, catalogue, engagement,
                popular, active, byType, Instant.now());
    }

    /**
     * Paginated user listing with an optional search and status filter.
     *
     * <p>Activity counts for the whole page are resolved in one query rather
     * than two per row, keeping this at a fixed query cost.
     */
    public PageResponse<AdminUserResponse> findUsers(String search, Boolean enabled,
                                                     Pageable pageable) {
        var page = userRepository.search(search, enabled, pageable);
        if (page.isEmpty()) {
            return PageResponse.from(page, user -> toAdminUser(user, 0L, 0L));
        }
        var userIds = page.getContent().stream().map(User::getId).toList();
        var ratingCounts = new java.util.HashMap<Long, Long>();
        var reviewCounts = new java.util.HashMap<Long, Long>();
        statisticsRepository.findActivityCounts(userIds).forEach(row -> {
            ratingCounts.put(row.getUserId(), row.getRatingCount());
            reviewCounts.put(row.getUserId(), row.getReviewCount());
        });
        return PageResponse.from(page, user -> toAdminUser(user,
                ratingCounts.getOrDefault(user.getId(), 0L),
                reviewCounts.getOrDefault(user.getId(), 0L)));
    }

    /**
     * Enables or disables an account.
     *
     * <p>Disabling also revokes every refresh token. Without that the user would
     * keep working until their access token expired and could silently refresh
     * afterwards, so the block would be largely cosmetic.
     */
    @Transactional
    public AdminUserResponse setEnabled(Long adminId, Long userId, boolean enabled, String reason) {
        if (adminId.equals(userId) && !enabled) {
            // Prevents an administrator locking themselves out.
            throw new BadRequestException("You cannot disable your own account");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setEnabled(enabled);
        userRepository.save(user);

        if (!enabled) {
            refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        }
        log.info("Admin {} {} account {}{}", adminId, enabled ? "enabled" : "disabled", userId,
                reason == null || reason.isBlank() ? "" : " (reason: " + reason + ")");
        // Single-user path: two extra counts here are not an N+1.
        return toAdminUser(user,
                ratingRepository.countByUserId(userId),
                reviewRepository.countByUserId(userId));
    }

    private AdminUserResponse toAdminUser(User user, long ratingCount, long reviewCount) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.isEnabled(), user.isEmailVerified(),
                user.getRoles().stream().map(Role::getName).sorted().toList(),
                ratingCount, reviewCount,
                user.getCreatedAt(), user.getLastLoginAt());
    }
}
