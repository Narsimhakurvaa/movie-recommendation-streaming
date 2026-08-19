package com.cinevault.interaction.repository;

import com.cinevault.interaction.domain.RecommendationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    @Query(value = """
           select l from RecommendationLog l join fetch l.movie
           where l.user.id = :userId
           """,
            countQuery = "select count(l) from RecommendationLog l where l.user.id = :userId")
    Page<RecommendationLog> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /** Distribution of served recommendation types, for the admin dashboard. */
    @Query("""
           select l.recommendationType as type, count(l) as total
           from RecommendationLog l
           group by l.recommendationType
           """)
    List<TypeCount> countByType();

    long countByGeneratedAtAfter(Instant threshold);

    /** Retention: the log grows unboundedly and is only useful while recent. */
    @Modifying
    @Query("delete from RecommendationLog l where l.generatedAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);

    interface TypeCount {
        String getType();

        long getTotal();
    }
}
