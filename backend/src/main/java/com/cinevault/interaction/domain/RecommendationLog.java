package com.cinevault.interaction.domain;

import com.cinevault.catalogue.domain.Movie;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A record of a recommendation that was actually served.
 *
 * <p>Persisting these makes "recommendation history" a real user-facing feature
 * and, more importantly, makes the engine measurable: served recommendations
 * can later be joined against watch history to compute click-through, which is
 * the only honest way to tune the blend weights.
 */
@Entity
@Table(name = "recommendation_logs")
public class RecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "recommendation_type", nullable = false, length = 32)
    private String recommendationType;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal score;

    @Column(length = 255)
    private String reason;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt = Instant.now();

    protected RecommendationLog() {
        // required by JPA
    }

    public RecommendationLog(User user, Movie movie, String recommendationType,
                             BigDecimal score, String reason) {
        this.user = user;
        this.movie = movie;
        this.recommendationType = recommendationType;
        this.score = score;
        this.reason = reason;
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

    public String getRecommendationType() {
        return recommendationType;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecommendationLog log)) {
            return false;
        }
        return id != null && id.equals(log.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
