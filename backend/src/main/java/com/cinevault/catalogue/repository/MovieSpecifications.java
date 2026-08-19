package com.cinevault.catalogue.repository;

import com.cinevault.catalogue.domain.Genre;
import com.cinevault.catalogue.domain.Movie;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Composable filters for movie discovery.
 *
 * <p>Using specifications keeps {@link MovieRepository} from growing a
 * combinatorial explosion of {@code findByGenreAndYearAndLanguageOrderBy...}
 * methods: the six filters below can be combined in any of their 64
 * permutations from a single query path.
 *
 * <p>Every predicate is built through the Criteria API, so all user input
 * arrives as a bound parameter and SQL injection is structurally impossible.
 */
public final class MovieSpecifications {

    private MovieSpecifications() {
    }

    /** Case-insensitive substring match over title and original title. */
    public static Specification<Movie> titleContains(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("originalTitle")), pattern));
    }

    /**
     * Restricts to films carrying ANY of the given genre slugs.
     *
     * <p>{@code distinct} is applied because the join multiplies rows when a
     * film matches several of the requested genres.
     */
    public static Specification<Movie> hasAnyGenreSlug(Collection<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Movie, Genre> genres = root.join("genres", JoinType.INNER);
            return genres.get("slug").in(slugs);
        };
    }

    /**
     * Restricts to films carrying EVERY given genre slug.
     *
     * <p>Implemented as a correlated count rather than repeated joins, which
     * would otherwise require one join per requested genre.
     */
    public static Specification<Movie> hasAllGenreSlugs(Collection<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            var subquery = query.subquery(Long.class);
            var subRoot = subquery.from(Movie.class);
            Join<Movie, Genre> genres = subRoot.join("genres", JoinType.INNER);
            subquery.select(cb.count(genres.get("id")))
                    .where(cb.and(cb.equal(subRoot.get("id"), root.get("id")),
                            genres.get("slug").in(slugs)));
            return cb.equal(subquery, (long) slugs.size());
        };
    }

    public static Specification<Movie> releasedFrom(Integer year) {
        if (year == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(
                root.get("releaseDate"), LocalDate.of(year, 1, 1));
    }

    public static Specification<Movie> releasedUntil(Integer year) {
        if (year == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(
                root.get("releaseDate"), LocalDate.of(year, 12, 31));
    }

    /** Filters on the provider rating (0-10 scale). */
    public static Specification<Movie> ratedAtLeast(BigDecimal minimum) {
        if (minimum == null || minimum.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("externalRating"), minimum);
    }

    public static Specification<Movie> ratedAtMost(BigDecimal maximum) {
        if (maximum == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("externalRating"), maximum);
    }

    public static Specification<Movie> inLanguages(Collection<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("originalLanguage").in(languages);
    }

    public static Specification<Movie> runtimeBetween(Integer min, Integer max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (min != null) {
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(root.get("runtimeMinutes"), min));
            }
            if (max != null) {
                predicate = cb.and(predicate,
                        cb.lessThanOrEqualTo(root.get("runtimeMinutes"), max));
            }
            return predicate;
        };
    }

    /**
     * Adult titles are excluded unless explicitly requested. Applied as a
     * default so that forgetting the filter fails safe rather than exposing
     * unwanted content.
     */
    public static Specification<Movie> adultVisibility(boolean includeAdult) {
        if (includeAdult) {
            return null;
        }
        return (root, query, cb) -> cb.isFalse(root.get("adult"));
    }

    /**
     * Combines the supplied specifications with AND, ignoring nulls.
     *
     * <p>Each builder above returns {@code null} when its input is absent, so
     * callers can pass everything unconditionally and let this method decide
     * what actually constrains the query.
     */
    @SafeVarargs
    public static Specification<Movie> allOf(Specification<Movie>... specifications) {
        Specification<Movie> combined = null;
        for (Specification<Movie> specification : specifications) {
            if (specification == null) {
                continue;
            }
            combined = (combined == null) ? specification : combined.and(specification);
        }
        return combined;
    }
}
