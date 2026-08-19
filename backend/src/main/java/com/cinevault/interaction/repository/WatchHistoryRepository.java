package com.cinevault.interaction.repository;

import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.domain.WatchHistoryEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WatchHistoryRepository extends JpaRepository<WatchHistoryEntry, Long> {

    @Query(value = """
           select h from WatchHistoryEntry h
           join fetch h.movie m
           where h.user.id = :userId
           """,
            countQuery = "select count(h) from WatchHistoryEntry h where h.user.id = :userId")
    Page<WatchHistoryEntry> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Distinct films the user has interacted with, most recent first.
     *
     * <p>Used to exclude already-seen titles from recommendations and to build
     * the "continue exploring" rail.
     */
    @Query("""
           select h.movie.id from WatchHistoryEntry h
           where h.user.id = :userId
           group by h.movie.id
           order by max(h.occurredAt) desc
           """)
    List<Long> findRecentMovieIds(@Param("userId") Long userId, Pageable pageable);

    @Query("select distinct h.movie.id from WatchHistoryEntry h where h.user.id = :userId")
    List<Long> findAllInteractedMovieIds(@Param("userId") Long userId);

    /**
     * Implicit genre affinity from browsing behaviour, weighted per interaction
     * type. Complements the explicit rating signal for users who watch a lot
     * but rate little.
     */
    @Query("""
           select g.id as id, count(h.id) as weight
           from WatchHistoryEntry h join h.movie m join m.genres g
           where h.user.id = :userId and h.interactionType in :types
           group by g.id
           """)
    List<RatingRepository.AffinityRow> findGenreAffinityFromHistory(
            @Param("userId") Long userId, @Param("types") List<InteractionType> types);

    long countByUserId(Long userId);

    long countByMovieId(Long movieId);

    long countByOccurredAtAfter(Instant threshold);

    boolean existsByUserIdAndMovieIdAndInteractionType(Long userId, Long movieId, InteractionType type);
}
