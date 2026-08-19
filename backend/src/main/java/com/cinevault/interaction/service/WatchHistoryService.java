package com.cinevault.interaction.service;

import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.catalogue.service.MovieMapper;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.exception.BadRequestException;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.domain.WatchHistoryEntry;
import com.cinevault.interaction.dto.InteractionDtos.WatchHistoryResponse;
import com.cinevault.interaction.repository.WatchHistoryRepository;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Records and reads user interaction history.
 *
 * <p>These events are the implicit half of the recommendation signal: they
 * capture what a user actually does, which is often more honest than what they
 * explicitly rate.
 *
 * <h2>Deduplication</h2>
 * <p>Low-value events are deduplicated. Opening the same detail page thirty
 * times is not thirty times the evidence, and recording it as such would let
 * idle browsing swamp deliberate ratings. High-value events
 * ({@code STARTED_WATCHING}, {@code COMPLETED}) are always appended because
 * rewatching genuinely is a stronger signal.
 */
@Service
@Transactional(readOnly = true)
public class WatchHistoryService {

    private static final Logger log = LoggerFactory.getLogger(WatchHistoryService.class);

    private final WatchHistoryRepository watchHistoryRepository;
    private final MovieRepository movieRepository;
    private final UserRepository userRepository;
    private final MovieMapper movieMapper;

    public WatchHistoryService(WatchHistoryRepository watchHistoryRepository,
                               MovieRepository movieRepository,
                               UserRepository userRepository,
                               MovieMapper movieMapper) {
        this.watchHistoryRepository = watchHistoryRepository;
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
        this.movieMapper = movieMapper;
    }

    /**
     * Records an interaction.
     *
     * @param progressPercent playback progress, or {@code null} when not applicable
     */
    @Transactional
    public void record(Long userId, Long movieId, InteractionType type, Integer progressPercent) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie", movieId);
        }
        if (isLowValue(type)
                && watchHistoryRepository.existsByUserIdAndMovieIdAndInteractionType(
                        userId, movieId, type)) {
            return;
        }

        var entry = new WatchHistoryEntry(userRepository.getReferenceById(userId),
                movieRepository.getReferenceById(movieId), type);
        if (progressPercent != null) {
            entry.setProgressPercent(progressPercent.shortValue());
        }
        watchHistoryRepository.save(entry);
        log.trace("Recorded {} for user {} on movie {}", type, userId, movieId);
    }

    /** Parses and records an interaction supplied by the client. */
    @Transactional
    public void record(Long userId, Long movieId, String rawType, Integer progressPercent) {
        record(userId, movieId, parseType(rawType), progressPercent);
    }

    public PageResponse<WatchHistoryResponse> findForUser(Long userId, Pageable pageable) {
        return PageResponse.from(watchHistoryRepository.findByUserId(userId, pageable),
                entry -> new WatchHistoryResponse(entry.getId(),
                        movieMapper.toSummary(entry.getMovie()),
                        entry.getInteractionType().name(),
                        entry.getProgressPercent() == null
                                ? null : entry.getProgressPercent().intValue(),
                        entry.getOccurredAt()));
    }

    public long countForUser(Long userId) {
        return watchHistoryRepository.countByUserId(userId);
    }

    /**
     * Browsing-style events, which repeat freely and are deduplicated.
     */
    private static boolean isLowValue(InteractionType type) {
        return type == InteractionType.VIEWED_DETAILS
                || type == InteractionType.WATCHED_TRAILER
                || type == InteractionType.ADDED_TO_WATCHLIST;
    }

    private static InteractionType parseType(String raw) {
        try {
            return InteractionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new BadRequestException("Unknown interaction type: " + raw);
        }
    }
}
