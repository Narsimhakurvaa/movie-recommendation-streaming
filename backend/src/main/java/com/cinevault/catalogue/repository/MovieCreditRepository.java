package com.cinevault.catalogue.repository;

import com.cinevault.catalogue.domain.MovieCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MovieCreditRepository extends JpaRepository<MovieCredit, Long> {

    /**
     * Bulk-loads credits for many films at once.
     *
     * <p>The recommendation service needs cast and crew for its whole candidate
     * pool. Fetching them per film would be a textbook N+1; this is one query.
     */
    @Query("""
           select c from MovieCredit c
           join fetch c.person
           where c.movie.id in :movieIds
           order by c.displayOrder
           """)
    List<MovieCredit> findAllByMovieIdIn(@Param("movieIds") Collection<Long> movieIds);
}
