package com.cinevault.interaction.service;

import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.domain.Rating;
import com.cinevault.interaction.dto.InteractionDtos.RatingResponse;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Creating, updating and removing a user's film ratings.
 *
 * <h2>Aggregate consistency</h2>
 * <p>{@code movies.average_rating} and {@code movies.rating_count} are NOT
 * updated here. A database trigger owns them (migration V4), which keeps them
 * correct even for writes that never pass through this service and removes any
 * possibility of the two drifting apart under concurrency. This service simply
 * re-reads the movie afterwards to report the new values.
 */
@Service
@Transactional(readOnly = true)
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final WatchHistoryService watchHistoryService;

    public RatingService(RatingRepository ratingRepository,
                         MovieRepository movieRepository,
                         UserRepository userRepository,
                         WatchHistoryService watchHistoryService) {
        this.ratingRepository = ratingRepository;
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
        this.watchHistoryService = watchHistoryService;
    }

    /**
     * Creates or updates the caller's rating for a film.
     *
     * <p>Upsert semantics: the API exposes both POST and PUT for familiarity,
     * and both land here. Re-rating a film is a normal action, not a conflict.
     */
    @Transactional
    public RatingResponse rate(Long userId, Long movieId, int score) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie", movieId);
        }

        Rating rating = ratingRepository.findByUserIdAndMovieId(userId, movieId)
                .map(existing -> {
                    existing.setScore(score);
                    return existing;
                })
                .orElseGet(() -> new Rating(userRepository.getReferenceById(userId),
                        movieRepository.getReferenceById(movieId), score));

        ratingRepository.saveAndFlush(rating);
        watchHistoryService.record(userId, movieId, InteractionType.RATED, null);
        log.debug("User {} rated movie {} as {}", userId, movieId, score);

        return buildResponse(movieId, score, rating.getUpdatedAt());
    }

    /**
     * Deletes the caller's rating.
     *
     * @throws ResourceNotFoundException if they had not rated the film
     */
    @Transactional
    public void deleteRating(Long userId, Long movieId) {
        var existing = ratingRepository.findByUserIdAndMovieId(userId, movieId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You have not rated this film"));
        ratingRepository.delete(existing);
        ratingRepository.flush();
        log.debug("User {} removed their rating for movie {}", userId, movieId);
    }

    /** The caller's own score, or {@code null} if they have not rated it. */
    public Integer findUserRating(Long userId, Long movieId) {
        return ratingRepository.findByUserIdAndMovieId(userId, movieId)
                .map(rating -> (int) rating.getScore())
                .orElse(null);
    }

    public long countForUser(Long userId) {
        return ratingRepository.countByUserId(userId);
    }

    /**
     * Reads back the trigger-maintained aggregates.
     *
     * <p>Uses a projection query rather than a loaded entity. The rating write
     * is flushed above so the trigger has fired, but a {@code Movie} already in
     * the persistence context would still return its stale in-memory values,
     * because Hibernate has no way to know the database changed the row.
     */
    private RatingResponse buildResponse(Long movieId, int score, Instant updatedAt) {
        var aggregate = movieRepository.findRatingAggregate(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", movieId));
        return new RatingResponse(movieId, score,
                aggregate.getAverageRating(), aggregate.getRatingCount(),
                updatedAt == null ? Instant.now() : updatedAt);
    }
}
