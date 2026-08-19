package com.cinevault.interaction.repository;

import com.cinevault.interaction.domain.Review;
import com.cinevault.interaction.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Visible reviews for a film.
     *
     * <p>Fetch-joins the author because every rendered review shows a name and
     * avatar; without it a 20-review page would issue 21 queries.
     */
    @Query("""
           select r from Review r join fetch r.user
           where r.movie.id = :movieId and r.status <> com.cinevault.interaction.domain.ReviewStatus.HIDDEN
           """)
    Page<Review> findVisibleByMovieId(@Param("movieId") Long movieId, Pageable pageable);

    @Query("select r from Review r join fetch r.movie where r.user.id = :userId")
    Page<Review> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /** Moderation queue: every review regardless of status. */
    @Query("select r from Review r join fetch r.user join fetch r.movie where (:status is null or r.status = :status)")
    Page<Review> findForModeration(@Param("status") ReviewStatus status, Pageable pageable);

    Optional<Review> findByUserIdAndMovieId(Long userId, Long movieId);

    boolean existsByUserIdAndMovieId(Long userId, Long movieId);

    long countByMovieIdAndStatusNot(Long movieId, ReviewStatus status);

    long countByStatus(ReviewStatus status);

    long countByUserId(Long userId);
}
