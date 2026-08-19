package com.cinevault.admin.repository;

import com.cinevault.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Reporting queries for the admin dashboard.
 *
 * <p>Kept apart from the domain repositories because these are analytical
 * aggregates serving one screen, not part of any module's core contract. Native
 * SQL is used where the grouping is easier to express and to read than the JPQL
 * equivalent; every parameter is still bound, never concatenated.
 */
public interface AdminStatisticsRepository extends JpaRepository<User, Long> {

    /** Films ranked by total recorded interactions. */
    @Query(value = """
            select m.id            as id,
                   m.title         as title,
                   count(h.id)     as interactions,
                   m.average_rating as averageRating,
                   m.rating_count  as ratingCount
              from movies m
              join watch_history h on h.movie_id = m.id
             group by m.id, m.title, m.average_rating, m.rating_count
             order by count(h.id) desc
             limit :limit
            """, nativeQuery = true)
    List<PopularMovieRow> findMostInteractedMovies(@Param("limit") int limit);

    /** Users ranked by total recorded interactions. */
    @Query(value = """
            select u.id           as id,
                   u.display_name as displayName,
                   u.email        as email,
                   count(h.id)    as interactions
              from users u
              join watch_history h on h.user_id = u.id
             group by u.id, u.display_name, u.email
             order by count(h.id) desc
             limit :limit
            """, nativeQuery = true)
    List<ActiveUserRow> findMostActiveUsers(@Param("limit") int limit);

    /**
     * Rating and review counts for many users at once.
     *
     * <p>Exists specifically to keep the admin user listing free of N+1: without
     * it, rendering a 20-row page would issue 40 count queries.
     */
    @Query(value = """
            select u.id as userId,
                   (select count(*) from ratings r where r.user_id = u.id) as ratingCount,
                   (select count(*) from reviews v where v.user_id = u.id) as reviewCount
              from users u
             where u.id in (:userIds)
            """, nativeQuery = true)
    List<UserActivityRow> findActivityCounts(@Param("userIds") Collection<Long> userIds);

    interface PopularMovieRow {
        Long getId();

        String getTitle();

        long getInteractions();

        BigDecimal getAverageRating();

        int getRatingCount();
    }

    interface ActiveUserRow {
        Long getId();

        String getDisplayName();

        String getEmail();

        long getInteractions();
    }

    interface UserActivityRow {
        Long getUserId();

        long getRatingCount();

        long getReviewCount();
    }
}
