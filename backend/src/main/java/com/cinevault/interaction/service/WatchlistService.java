package com.cinevault.interaction.service;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.catalogue.service.MovieMapper;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.exception.DuplicateResourceException;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.domain.WatchlistItem;
import com.cinevault.interaction.dto.InteractionDtos.WatchlistItemResponse;
import com.cinevault.interaction.dto.InteractionDtos.WatchlistStatus;
import com.cinevault.interaction.repository.WatchlistRepository;
import com.cinevault.user.domain.User;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing the films a user has saved for later.
 *
 * <p>Saving also records a watch-history interaction, because intent to watch
 * is a genuine preference signal the recommendation engine should see.
 */
@Service
@Transactional(readOnly = true)
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistRepository watchlistRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final WatchHistoryService watchHistoryService;
    private final MovieMapper movieMapper;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            MovieRepository movieRepository,
                            UserRepository userRepository,
                            WatchHistoryService watchHistoryService,
                            MovieMapper movieMapper) {
        this.watchlistRepository = watchlistRepository;
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
        this.watchHistoryService = watchHistoryService;
        this.movieMapper = movieMapper;
    }

    public PageResponse<WatchlistItemResponse> findForUser(Long userId, Pageable pageable) {
        return PageResponse.from(watchlistRepository.findByUserId(userId, pageable), this::toResponse);
    }

    /**
     * Saves a film.
     *
     * @throws DuplicateResourceException if it is already saved
     */
    @Transactional
    public WatchlistItemResponse add(Long userId, Long movieId, String note) {
        // Pre-checking gives a clear 409 rather than a raw constraint error,
        // but the database constraint remains the actual guarantee: two
        // concurrent requests can both pass this check.
        if (watchlistRepository.existsByUserIdAndMovieId(userId, movieId)) {
            throw new DuplicateResourceException("This film is already in your watchlist");
        }

        User user = userRepository.getReferenceById(userId);
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", movieId));

        WatchlistItem item = new WatchlistItem(user, movie);
        item.setNote(note);

        try {
            WatchlistItem saved = watchlistRepository.save(item);
            watchHistoryService.record(userId, movieId, InteractionType.ADDED_TO_WATCHLIST, null);
            log.debug("User {} saved movie {}", userId, movieId);
            return toResponse(saved);
        } catch (DataIntegrityViolationException raced) {
            // Lost a race with a concurrent add; the outcome the caller wanted
            // has still been achieved, so report it as a duplicate not a 500.
            throw new DuplicateResourceException("This film is already in your watchlist");
        }
    }

    /**
     * Removes a film.
     *
     * @throws ResourceNotFoundException if it was not saved
     */
    @Transactional
    public void remove(Long userId, Long movieId) {
        long deleted = watchlistRepository.deleteByUserIdAndMovieId(userId, movieId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("This film is not in your watchlist");
        }
        log.debug("User {} removed movie {} from watchlist", userId, movieId);
    }

    /** Cheap membership check for the detail page's save button. */
    public WatchlistStatus status(Long userId, Long movieId) {
        return new WatchlistStatus(movieId,
                watchlistRepository.existsByUserIdAndMovieId(userId, movieId));
    }

    public long countForUser(Long userId) {
        return watchlistRepository.countByUserId(userId);
    }

    private WatchlistItemResponse toResponse(WatchlistItem item) {
        return new WatchlistItemResponse(item.getId(),
                movieMapper.toSummary(item.getMovie()).withUserState(true, null),
                item.getNote(), item.getAddedAt());
    }
}
