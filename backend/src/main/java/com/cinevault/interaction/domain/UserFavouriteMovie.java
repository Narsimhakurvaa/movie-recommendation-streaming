package com.cinevault.interaction.domain;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** A film the user marked as a personal favourite. */
@Entity
@Table(name = "user_favourite_movies")
public class UserFavouriteMovie {

    @EmbeddedId
    private Key key = new Key();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("movieId")
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    protected UserFavouriteMovie() {
        // required by JPA
    }

    public UserFavouriteMovie(User user, Movie movie) {
        this.user = user;
        this.movie = movie;
        this.key = new Key(user.getId(), movie.getId());
    }

    public User getUser() {
        return user;
    }

    public Movie getMovie() {
        return movie;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    /** Composite primary key of (user, movie). */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "movie_id")
        private Long movieId;

        public Key() {
        }

        public Key(Long userId, Long movieId) {
            this.userId = userId;
            this.movieId = movieId;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getMovieId() {
            return movieId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key k)) {
                return false;
            }
            return Objects.equals(userId, k.userId) && Objects.equals(movieId, k.movieId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, movieId);
        }
    }
}
