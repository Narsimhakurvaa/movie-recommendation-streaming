package com.cinevault.interaction.repository;

import com.cinevault.interaction.domain.WatchlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    /**
     * The user's saved films. Fetch-joins the movie and its genres because the
     * watchlist page renders full cards.
     */
    @Query(value = """
           select w from WatchlistItem w
           join fetch w.movie m
           left join fetch m.genres
           where w.user.id = :userId
           """,
            countQuery = "select count(w) from WatchlistItem w where w.user.id = :userId")
    Page<WatchlistItem> findByUserId(@Param("userId") Long userId, Pageable pageable);

    Optional<WatchlistItem> findByUserIdAndMovieId(Long userId, Long movieId);

    boolean existsByUserIdAndMovieId(Long userId, Long movieId);

    long deleteByUserIdAndMovieId(Long userId, Long movieId);

    @Query("select w.movie.id from WatchlistItem w where w.user.id = :userId")
    List<Long> findMovieIdsByUserId(@Param("userId") Long userId);

    /**
     * Bulk membership check.
     *
     * <p>Lets a listing page mark every card as saved or not with one query
     * instead of one lookup per card.
     */
    @Query("select w.movie.id from WatchlistItem w where w.user.id = :userId and w.movie.id in :movieIds")
    List<Long> findSavedMovieIdsAmong(@Param("userId") Long userId,
                                      @Param("movieIds") Collection<Long> movieIds);

    long countByUserId(Long userId);
}
