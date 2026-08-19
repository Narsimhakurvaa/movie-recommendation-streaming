package com.cinevault.interaction.domain;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.common.domain.BaseEntity;
import com.cinevault.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

/**
 * A user's 1-5 score for a film.
 *
 * <p>Uniqueness per (user, movie) is enforced by a database constraint rather
 * than a service-level check, so two concurrent submissions cannot both pass a
 * "does it exist?" test and insert duplicates.
 */
@Entity
@Table(name = "ratings",
        uniqueConstraints = @UniqueConstraint(name = "uq_ratings_user_movie",
                columnNames = {"user_id", "movie_id"}))
public class Rating extends BaseEntity {

    /** Lowest permitted score; also enforced by a DB check constraint. */
    public static final int MIN_SCORE = 1;
    /** Highest permitted score; also enforced by a DB check constraint. */
    public static final int MAX_SCORE = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(nullable = false)
    private short score;

    protected Rating() {
        // required by JPA
    }

    public Rating(User user, Movie movie, int score) {
        this.user = user;
        this.movie = movie;
        setScore(score);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Movie getMovie() {
        return movie;
    }

    public short getScore() {
        return score;
    }

    /** Guards the range in Java too, so a bug fails fast rather than at the DB. */
    public final void setScore(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(
                    "Rating score must be between %d and %d".formatted(MIN_SCORE, MAX_SCORE));
        }
        this.score = (short) score;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Rating rating)) {
            return false;
        }
        return id != null && id.equals(rating.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
