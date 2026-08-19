package com.cinevault.catalogue.repository;

import com.cinevault.catalogue.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findBySlug(String slug);

    Optional<Genre> findByNameIgnoreCase(String name);

    List<Genre> findByIdIn(Collection<Long> ids);

    List<Genre> findAllByOrderByNameAsc();

    /**
     * Genre facets with film counts for the discovery sidebar.
     *
     * <p>A LEFT JOIN is used so that genres with no films still appear (with a
     * count of zero) rather than silently vanishing from the filter list.
     */
    @Query(value = """
           select g.id as id, g.name as name, g.slug as slug, count(mg.movie_id) as total
           from genres g
           left join movie_genres mg on mg.genre_id = g.id
           group by g.id, g.name, g.slug
           order by g.name
           """, nativeQuery = true)
    List<GenreCountProjection> findAllWithMovieCounts();

    /** Projection interface for the native facet query above. */
    interface GenreCountProjection {
        Long getId();

        String getName();

        String getSlug();

        long getTotal();
    }
}
