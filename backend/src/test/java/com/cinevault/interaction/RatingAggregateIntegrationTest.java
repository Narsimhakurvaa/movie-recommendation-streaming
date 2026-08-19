package com.cinevault.interaction;

import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.interaction.service.RatingService;
import com.cinevault.support.PostgresIntegrationTest;
import com.cinevault.user.domain.Role;
import com.cinevault.user.domain.User;
import com.cinevault.user.repository.RoleRepository;
import com.cinevault.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the rating aggregate trigger and the constraints that
 * protect rating integrity.
 *
 * <p>These deliberately run against real PostgreSQL: the aggregate is
 * maintained by a database trigger and the uniqueness rule is an index, so
 * neither exists under H2 and neither can be verified with mocks.
 */
class RatingAggregateIntegrationTest extends PostgresIntegrationTest {

    @Autowired private RatingService ratingService;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long anyMovieId() {
        return jdbcTemplate.queryForObject("select id from movies order by id limit 1", Long.class);
    }

    private User newUser(String email) {
        Role role = roleRepository.findByName("ROLE_USER").orElseThrow();
        User user = new User(email, "$2a$12$notarealhashusedonlyintests00000000000000000000000000", "Tester");
        user.addRole(role);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("the trigger updates the movie aggregate as ratings arrive")
    @Transactional
    void triggerMaintainsAggregate() {
        Long movieId = anyMovieId();
        Long userA = newUser("agg.a@example.com").getId();
        Long userB = newUser("agg.b@example.com").getId();

        ratingService.rate(userA, movieId, 5);
        var afterOne = movieRepository.findRatingAggregate(movieId).orElseThrow();
        int baseCount = afterOne.getRatingCount();

        ratingService.rate(userB, movieId, 3);
        var afterTwo = movieRepository.findRatingAggregate(movieId).orElseThrow();

        assertThat(afterTwo.getRatingCount())
                .as("the count must move with each new rating")
                .isEqualTo(baseCount + 1);
        assertThat(afterTwo.getAverageRating())
                .as("the trigger must recompute the mean, not leave it stale")
                .isNotEqualTo(afterOne.getAverageRating());
    }

    @Test
    @DisplayName("the aggregate is visible inside the writing transaction")
    @Transactional
    void aggregateVisibleWithinTransaction() {
        Long movieId = anyMovieId();
        Long userId = newUser("agg.same.tx@example.com").getId();

        var before = movieRepository.findRatingAggregate(movieId).orElseThrow();
        ratingService.rate(userId, movieId, 5);
        var after = movieRepository.findRatingAggregate(movieId).orElseThrow();

        // The service re-reads via a projection precisely because Hibernate's
        // first-level cache would otherwise hand back the pre-trigger values.
        assertThat(after.getRatingCount()).isEqualTo(before.getRatingCount() + 1);
    }

    @Test
    @DisplayName("re-rating updates in place rather than adding a second row")
    @Transactional
    void reRatingIsAnUpsert() {
        Long movieId = anyMovieId();
        Long userId = newUser("agg.rerate@example.com").getId();

        ratingService.rate(userId, movieId, 2);
        long countAfterFirst = ratingRepository.count();
        var aggregateAfterFirst = movieRepository.findRatingAggregate(movieId).orElseThrow();

        ratingService.rate(userId, movieId, 5);

        assertThat(ratingRepository.count())
                .as("one user may hold only one rating per film")
                .isEqualTo(countAfterFirst);
        assertThat(movieRepository.findRatingAggregate(movieId).orElseThrow().getRatingCount())
                .isEqualTo(aggregateAfterFirst.getRatingCount());
        assertThat(ratingRepository.findByUserIdAndMovieId(userId, movieId).orElseThrow().getScore())
                .isEqualTo((short) 5);
    }

    @Test
    @DisplayName("removing a rating rolls the aggregate back")
    @Transactional
    void deletingRatingUpdatesAggregate() {
        Long movieId = anyMovieId();
        Long userId = newUser("agg.delete@example.com").getId();

        var before = movieRepository.findRatingAggregate(movieId).orElseThrow();
        ratingService.rate(userId, movieId, 4);
        ratingService.deleteRating(userId, movieId);
        var after = movieRepository.findRatingAggregate(movieId).orElseThrow();

        assertThat(after.getRatingCount()).isEqualTo(before.getRatingCount());
    }

    @ParameterizedTest(name = "rejects a score of {0}")
    @ValueSource(ints = {0, 6, 9, -1})
    @DisplayName("the check constraint refuses a score outside 1-5")
    @Transactional
    void checkConstraintRejectsOutOfRangeScore(int score) {
        Long movieId = anyMovieId();
        Long userId = newUser("agg.range" + score + "@example.com").getId();

        // Bypasses the service so the database constraint is what fails,
        // proving validation is enforced at the last line of defence.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into ratings (user_id, movie_id, score) values (?, ?, ?)",
                userId, movieId, score))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ratings_score");
    }

    @Test
    @DisplayName("the unique index refuses a duplicate rating row")
    @Transactional
    void uniqueIndexRejectsDuplicate() {
        Long movieId = anyMovieId();
        Long userId = newUser("agg.dupe@example.com").getId();

        jdbcTemplate.update("insert into ratings (user_id, movie_id, score) values (?, ?, ?)",
                userId, movieId, 4);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into ratings (user_id, movie_id, score) values (?, ?, ?)",
                userId, movieId, 5))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ratings_user_movie");
    }

    @Test
    @DisplayName("the seeded catalogue is present and internally consistent")
    void seedDataIsUsable() {
        Integer movies = jdbcTemplate.queryForObject("select count(*) from movies", Integer.class);
        Integer genres = jdbcTemplate.queryForObject("select count(*) from genres", Integer.class);
        Integer orphanedLinks = jdbcTemplate.queryForObject(
                "select count(*) from movie_genres mg "
                        + "left join movies m on m.id = mg.movie_id where m.id is null",
                Integer.class);

        assertThat(movies).isNotNull().isPositive();
        assertThat(genres).isNotNull().isPositive();
        assertThat(orphanedLinks).isZero();
    }

    @Test
    @DisplayName("every seeded movie has a unique slug")
    void seededSlugsAreUnique() {
        List<String> duplicates = jdbcTemplate.queryForList(
                "select slug from movies group by slug having count(*) > 1", String.class);

        assertThat(duplicates).isEmpty();
    }

    @Test
    @DisplayName("stored aggregates agree with the underlying rating rows")
    void storedAggregatesMatchSourceRows() {
        // Guards against a trigger that drifts from the data it summarises.
        Integer mismatched = jdbcTemplate.queryForObject("""
                select count(*) from movies m
                where m.rating_count <> (
                    select count(*) from ratings r where r.movie_id = m.id)
                """, Integer.class);

        assertThat(mismatched).isZero();
    }

    @Test
    @DisplayName("average ratings stay inside the documented 1-5 range")
    void averageRatingsAreInRange() {
        List<BigDecimal> outOfRange = jdbcTemplate.queryForList(
                "select average_rating from movies "
                        + "where rating_count > 0 and (average_rating < 1 or average_rating > 5)",
                BigDecimal.class);

        assertThat(outOfRange).isEmpty();
    }
}
