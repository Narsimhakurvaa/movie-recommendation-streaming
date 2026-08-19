package com.cinevault.catalogue.repository;

import com.cinevault.catalogue.domain.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Catalogue access.
 *
 * <p>Extends {@link JpaSpecificationExecutor} so that the discovery endpoint can
 * compose its filters dynamically (see {@code MovieSpecifications}) instead of
 * this interface accumulating one query method per combination of criteria.
 */
public interface MovieRepository extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie> {

    /**
     * Loads a film with everything the detail page renders, in one round trip.
     *
     * <p>An entity graph is used rather than {@code join fetch} because
     * fetching two independent collections (genres and keywords) in a single
     * SQL statement produces a cartesian product. Hibernate resolves an entity
     * graph with separate selects, which is both correct and faster here.
     */
    @EntityGraph(attributePaths = {"genres", "keywords", "credits", "credits.person"})
    @Query("select m from Movie m where m.id = :id")
    Optional<Movie> findDetailById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"genres", "keywords", "credits", "credits.person"})
    @Query("select m from Movie m where m.slug = :slug")
    Optional<Movie> findDetailBySlug(@Param("slug") String slug);

    /** Batch variant used by the recommendation service to avoid N+1 loading. */
    @EntityGraph(attributePaths = {"genres"})
    @Query("select m from Movie m where m.id in :ids")
    List<Movie> findAllWithGenresByIdIn(@Param("ids") Collection<Long> ids);

    boolean existsBySlug(String slug);

    Optional<Movie> findBySlug(String slug);

    Optional<Movie> findByTmdbId(Integer tmdbId);

    /**
     * Type-ahead suggestions. Deliberately returns a projection rather than
     * entities: the dropdown needs four columns, not the whole row graph.
     */
    @Query("""
           select new com.cinevault.catalogue.repository.MovieSuggestion(
               m.id, m.title, m.slug, m.posterUrl, m.releaseDate)
           from Movie m
           where lower(m.title) like lower(concat(:prefix, '%'))
              or lower(m.title) like lower(concat('% ', :prefix, '%'))
           order by m.popularity desc
           """)
    List<MovieSuggestion> suggest(@Param("prefix") String prefix, Pageable pageable);

    /**
     * Trending: recent releases weighted by popularity.
     *
     * <p>Expressed in JPQL rather than as a Specification because the ordering
     * is a fixed business rule, not a user-supplied filter.
     */
    @Query("""
           select m from Movie m
           where m.releaseDate >= :since and m.adult = false
           order by m.popularity desc, m.releaseDate desc
           """)
    Page<Movie> findTrending(@Param("since") LocalDate since, Pageable pageable);

    @Query("select m from Movie m where m.adult = false order by m.popularity desc")
    Page<Movie> findPopular(Pageable pageable);

    /**
     * Top rated, using a vote-count floor so a single 5-star rating cannot put
     * an obscure title above a widely acclaimed one.
     */
    @Query("""
           select m from Movie m
           where m.externalVoteCount >= :minVotes and m.adult = false
           order by m.externalRating desc, m.externalVoteCount desc
           """)
    Page<Movie> findTopRated(@Param("minVotes") int minVotes, Pageable pageable);

    @Query("""
           select m from Movie m
           where m.releaseDate <= :today and m.adult = false
           order by m.releaseDate desc
           """)
    Page<Movie> findRecentReleases(@Param("today") LocalDate today, Pageable pageable);

    /**
     * Candidate pool for the recommendation engine.
     *
     * <p>Bounded on purpose. Scoring the entire catalogue would be wasteful and
     * would not change the outcome: titles outside the popularity head have
     * effectively no chance of ranking once personalisation is applied. The
     * caller passes a page size that defines the pool.
     */
    @Query("""
           select m from Movie m
           where m.adult = :includeAdult or m.adult = false
           order by m.popularity desc
           """)
    List<Movie> findCandidatePool(@Param("includeAdult") boolean includeAdult, Pageable pageable);

    @Query("select coalesce(avg(m.externalRating), 0) from Movie m where m.externalVoteCount > 0")
    double averageExternalRating();

    /**
     * Reads the trigger-maintained rating aggregates straight from the table.
     *
     * <p>Necessary because the aggregates are updated by a database trigger,
     * which Hibernate cannot observe: a loaded {@code Movie} in the persistence
     * context would still hold the pre-trigger values. This projection bypasses
     * the first-level cache and always reflects committed state.
     */
    @Query("select m.averageRating as averageRating, m.ratingCount as ratingCount "
            + "from Movie m where m.id = :movieId")
    Optional<RatingAggregate> findRatingAggregate(@Param("movieId") Long movieId);

    /** Trigger-maintained rating aggregates for one film. */
    interface RatingAggregate {
        java.math.BigDecimal getAverageRating();

        int getRatingCount();
    }

    @Query("select count(m) from Movie m where m.releaseDate >= :since")
    long countReleasedSince(@Param("since") LocalDate since);
}
