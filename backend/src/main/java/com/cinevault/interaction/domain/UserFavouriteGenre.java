package com.cinevault.interaction.domain;

import com.cinevault.catalogue.domain.Genre;
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
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A genre the user explicitly selected during onboarding.
 *
 * <p>This is the single most valuable signal for a cold-start user, and
 * {@code weight} preserves the order in which they were chosen so the first
 * pick can count for more than the last.
 */
@Entity
@Table(name = "user_favourite_genres")
public class UserFavouriteGenre {

    @EmbeddedId
    private Key key = new Key();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("genreId")
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal weight = BigDecimal.ONE;

    protected UserFavouriteGenre() {
        // required by JPA
    }

    public UserFavouriteGenre(User user, Genre genre, BigDecimal weight) {
        this.user = user;
        this.genre = genre;
        this.weight = weight;
        this.key = new Key(user.getId(), genre.getId());
    }

    public User getUser() {
        return user;
    }

    public Genre getGenre() {
        return genre;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    /** Composite primary key of (user, genre). */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "genre_id")
        private Long genreId;

        public Key() {
        }

        public Key(Long userId, Long genreId) {
            this.userId = userId;
            this.genreId = genreId;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getGenreId() {
            return genreId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key k)) {
                return false;
            }
            return Objects.equals(userId, k.userId) && Objects.equals(genreId, k.genreId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, genreId);
        }
    }
}
