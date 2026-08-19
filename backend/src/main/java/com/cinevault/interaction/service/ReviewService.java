package com.cinevault.interaction.service;

import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.exception.AccessDeniedAppException;
import com.cinevault.common.exception.BadRequestException;
import com.cinevault.common.exception.DuplicateResourceException;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.Review;
import com.cinevault.interaction.domain.ReviewStatus;
import com.cinevault.interaction.dto.InteractionDtos.AuthorSummary;
import com.cinevault.interaction.dto.InteractionDtos.ReviewRequest;
import com.cinevault.interaction.dto.InteractionDtos.ReviewResponse;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.interaction.repository.ReviewRepository;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Writing, editing, reading and moderating reviews.
 *
 * <h2>Authorisation</h2>
 * <p>Ownership is enforced here, in the service, not in the controller. A
 * controller-only check would be bypassed by any future caller of this method,
 * and "can this principal modify this row?" is a domain rule rather than an
 * HTTP concern. Administrators may moderate but may not silently rewrite
 * someone else's words.
 *
 * <h2>Abuse prevention</h2>
 * <p>One review per user per film (enforced by a database constraint), a
 * minimum body length that rules out low-effort spam, and a repetition check
 * that rejects padded filler.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /**
     * Rejects bodies where one character dominates, which is the cheapest way
     * to pad a post past a length check ("aaaaaaaa...").
     */
    private static final double MAX_SINGLE_CHARACTER_RATIO = 0.4;

    private final ReviewRepository reviewRepository;
    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         RatingRepository ratingRepository,
                         MovieRepository movieRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.ratingRepository = ratingRepository;
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
    }

    /**
     * Visible reviews for a film.
     *
     * <p>Author ratings are resolved for the whole page in one query rather
     * than per review, keeping this to a fixed query count.
     */
    public PageResponse<ReviewResponse> findForMovie(Long movieId, Pageable pageable, Long currentUserId) {
        var page = reviewRepository.findVisibleByMovieId(movieId, pageable);
        var authorRatings = loadAuthorRatings(movieId, page.getContent());
        return PageResponse.from(page,
                review -> toResponse(review, currentUserId,
                        authorRatings.get(review.getUser().getId())));
    }

    public PageResponse<ReviewResponse> findByUser(Long userId, Pageable pageable) {
        var page = reviewRepository.findByUserId(userId, pageable);
        // One user, many films: fetch all their scores in a single query.
        var scores = new java.util.HashMap<Long, Integer>();
        ratingRepository.findScoresByUserId(userId)
                .forEach(row -> scores.put(row.getMovieId(), row.getScore().intValue()));
        return PageResponse.from(page,
                review -> toResponse(review, userId, scores.get(review.getMovie().getId())));
    }

    /** Author-id to their score for this film, for one page of reviews. */
    private java.util.Map<Long, Integer> loadAuthorRatings(Long movieId, List<Review> reviews) {
        if (reviews.isEmpty()) {
            return java.util.Map.of();
        }
        var authorIds = reviews.stream().map(r -> r.getUser().getId()).distinct().toList();
        var result = new java.util.HashMap<Long, Integer>();
        ratingRepository.findScoresForUsersOnMovie(movieId, authorIds)
                .forEach(row -> result.put(row.getUserId(), row.getScore().intValue()));
        return result;
    }

    /**
     * Publishes a review.
     *
     * @throws DuplicateResourceException if the user already reviewed this film
     */
    @Transactional
    public ReviewResponse create(Long userId, Long movieId, ReviewRequest request) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie", movieId);
        }
        if (reviewRepository.existsByUserIdAndMovieId(userId, movieId)) {
            throw new DuplicateResourceException(
                    "You have already reviewed this film. Edit your existing review instead.");
        }
        validateBody(request.body());

        Review review = new Review(userRepository.getReferenceById(userId),
                movieRepository.getReferenceById(movieId), request.body().trim());
        review.setTitle(trimToNull(request.title()));
        review.setContainsSpoilers(Boolean.TRUE.equals(request.containsSpoilers()));

        Review saved = reviewRepository.save(review);
        log.debug("User {} reviewed movie {}", userId, movieId);
        return toResponse(saved, userId, findAuthorRating(userId, movieId));
    }

    /**
     * Edits a review.
     *
     * @throws AccessDeniedAppException if the caller does not own it
     */
    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));

        if (!review.isOwnedBy(userId)) {
            // Deliberately not distinguishing "not yours" from "does not exist"
            // beyond the status code; both are simply refused.
            throw new AccessDeniedAppException("You can only edit your own reviews");
        }
        validateBody(request.body());

        review.setBody(request.body().trim());
        review.setTitle(trimToNull(request.title()));
        review.setContainsSpoilers(Boolean.TRUE.equals(request.containsSpoilers()));
        return toResponse(reviewRepository.save(review), userId,
                findAuthorRating(userId, review.getMovie().getId()));
    }

    /**
     * Deletes a review. The owner may delete their own; an administrator may
     * delete any, for moderation.
     */
    @Transactional
    public void delete(Long userId, Long reviewId, boolean isAdmin) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));

        if (!review.isOwnedBy(userId) && !isAdmin) {
            throw new AccessDeniedAppException("You can only delete your own reviews");
        }
        reviewRepository.delete(review);
        log.info("Review {} deleted by user {} (admin={})", reviewId, userId, isAdmin);
    }

    /** Administrator moderation: change visibility without destroying content. */
    @Transactional
    public ReviewResponse moderate(Long reviewId, String rawStatus, String note, Long adminId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));

        ReviewStatus status;
        try {
            status = ReviewStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Unknown review status: " + rawStatus);
        }

        review.setStatus(status);
        review.setModerationNote(trimToNull(note));
        log.info("Review {} moderated to {} by admin {}", reviewId, status, adminId);
        return toResponse(reviewRepository.save(review), adminId,
                findAuthorRating(review.getUser().getId(), review.getMovie().getId()));
    }

    /** Moderation queue for administrators. */
    public PageResponse<ReviewResponse> findForModeration(String rawStatus, Pageable pageable,
                                                          Long adminId) {
        ReviewStatus status = null;
        if (rawStatus != null && !rawStatus.isBlank()) {
            try {
                status = ReviewStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new BadRequestException("Unknown review status: " + rawStatus);
            }
        }
        var page = reviewRepository.findForModeration(status, pageable);
        return PageResponse.from(page, review -> toResponse(review, adminId, null));
    }

    public long countForUser(Long userId) {
        return reviewRepository.countByUserId(userId);
    }

    /**
     * Rejects obvious spam that a length constraint alone would let through.
     */
    private void validateBody(String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.length() < 20) {
            throw new BadRequestException("Review must be at least 20 characters");
        }
        long distinct = trimmed.chars().filter(Character::isLetterOrDigit).distinct().count();
        if (distinct < 5) {
            throw new BadRequestException("Review does not appear to contain meaningful text");
        }
        // Single-character padding check.
        var counts = new java.util.HashMap<Character, Integer>();
        for (char c : trimmed.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max > trimmed.length() * MAX_SINGLE_CHARACTER_RATIO) {
            throw new BadRequestException("Review does not appear to contain meaningful text");
        }
    }

    /** Single-review lookup, used only on write paths where it is one query. */
    private Integer findAuthorRating(Long userId, Long movieId) {
        return ratingRepository.findByUserIdAndMovieId(userId, movieId)
                .map(rating -> (int) rating.getScore())
                .orElse(null);
    }

    private ReviewResponse toResponse(Review review, Long currentUserId, Integer authorRating) {
        var author = review.getUser();
        return new ReviewResponse(review.getId(),
                review.getMovie().getId(),
                review.getMovie().getTitle(),
                new AuthorSummary(author.getId(), author.getDisplayName(), author.getAvatarUrl()),
                review.getTitle(),
                review.getBody(),
                review.isContainsSpoilers(),
                review.getStatus().name(),
                authorRating,
                currentUserId != null && review.isOwnedBy(currentUserId),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
