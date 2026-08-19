package com.cinevault.interaction.repository;

import com.cinevault.interaction.domain.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByUserIdAndMovieId(Long userId, Long movieId);

    boolean existsByUserIdAndMovieId(Long userId, Long movieId);

    void deleteByUserIdAndMovieId(Long userId, Long movieId);

    @Query("select r from Rating r join fetch r.movie where r.user.id = :userId")
    Page<Rating> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * The signed user's own ratings as (movieId, score) pairs.
     *
     * <p>A projection rather than entities: the taste profile needs two
     * numbers per row, and hydrating full {@code Rating} objects with their
     * user and movie proxies would be pure overhead.
     */
    @Query("select r.movie.id as movieId, r.score as score from Rating r where r.user.id = :userId")
    List<MovieScore> findScoresByUserId(@Param("userId") Long userId);

    /**
     * Rating vectors of users who overlap with the target on at least one film.
     *
     * <p>This single query replaces what would otherwise be "find similar
     * users, then load each one's ratings" - an N+1 pattern that would issue
     * one query per neighbour. Restricting to users with actual overlap also
     * keeps the result proportional to the neighbourhood rather than to the
     * entire user base.
     *
     * <p>{@code limit} caps the rows returned so a pathological data set cannot
     * pull the whole ratings table into memory.
     */
    @Query(value = """
           select r.user_id as userId, r.movie_id as movieId, r.score as score
           from ratings r
           where r.user_id <> :userId
             and r.user_id in (
                   select distinct other.user_id
                   from ratings other
                   where other.user_id <> :userId
                     and other.movie_id in (
                           select mine.movie_id from ratings mine where mine.user_id = :userId)
                   limit :neighbourLimit)
           """, nativeQuery = true)
    List<NeighbourRating> findNeighbourRatings(@Param("userId") Long userId,
                                               @Param("neighbourLimit") int neighbourLimit);

    @Query("select coalesce(avg(r.score), 0) from Rating r where r.movie.id = :movieId")
    double averageScoreForMovie(@Param("movieId") Long movieId);

    long countByMovieId(Long movieId);

    long countByUserId(Long userId);

    /** Genre affinity derived from positively-rated films, in one aggregate query. */
    @Query("""
           select g.id as id, sum(r.score - 3) as weight
           from Rating r join r.movie m join m.genres g
           where r.user.id = :userId and r.score > 3
           group by g.id
           """)
    List<AffinityRow> findGenreAffinity(@Param("userId") Long userId);

    @Query("""
           select k.id as id, sum(r.score - 3) as weight
           from Rating r join r.movie m join m.keywords k
           where r.user.id = :userId and r.score > 3
           group by k.id
           """)
    List<AffinityRow> findKeywordAffinity(@Param("userId") Long userId);

    /**
     * Person affinity split by credit type, so directors and cast can be
     * weighted differently by the content strategy.
     */
    @Query("""
           select c.person.id as id, sum(r.score - 3) as weight
           from Rating r join r.movie m join m.credits c
           where r.user.id = :userId and r.score > 3 and c.creditType = :creditType
           group by c.person.id
           """)
    List<AffinityRow> findPersonAffinity(@Param("userId") Long userId,
                                         @Param("creditType")
                                         com.cinevault.catalogue.domain.CreditType creditType);

    @Query("""
           select m.originalLanguage as code, sum(r.score - 3) as weight
           from Rating r join r.movie m
           where r.user.id = :userId and r.score > 3 and m.originalLanguage is not null
           group by m.originalLanguage
           """)
    List<LanguageAffinityRow> findLanguageAffinity(@Param("userId") Long userId);

    @Query("select r.movie.id from Rating r where r.user.id = :userId and r.score >= :threshold")
    List<Long> findLikedMovieIds(@Param("userId") Long userId, @Param("threshold") int threshold);

    @Query("select r.movie.id from Rating r where r.user.id = :userId and r.movie.id in :movieIds")
    List<Long> findRatedMovieIdsAmong(@Param("userId") Long userId,
                                      @Param("movieIds") Collection<Long> movieIds);

    /**
     * Ratings for many (user, movie) pairs at once.
     *
     * <p>Used when rendering a page of reviews, each of which shows its
     * author's own score. Looking those up per review would be an N+1; this
     * resolves the whole page in one query.
     */
    @Query("""
           select r.user.id as userId, r.movie.id as movieId, r.score as score
           from Rating r
           where r.movie.id = :movieId and r.user.id in :userIds
           """)
    List<NeighbourRating> findScoresForUsersOnMovie(@Param("movieId") Long movieId,
                                                    @Param("userIds") Collection<Long> userIds);

    /** (movieId, score) pair used to build the taste profile. */
    interface MovieScore {
        Long getMovieId();

        Short getScore();
    }

    /** One cell of the user-item rating matrix. */
    interface NeighbourRating {
        Long getUserId();

        Long getMovieId();

        Short getScore();
    }

    /** Aggregated affinity toward an attribute (genre, keyword or person). */
    interface AffinityRow {
        Long getId();

        Double getWeight();
    }

    /** Aggregated affinity toward a language. */
    interface LanguageAffinityRow {
        String getCode();

        Double getWeight();
    }
}
