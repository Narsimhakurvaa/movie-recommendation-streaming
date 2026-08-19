package com.cinevault.interaction.domain;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * One recorded interaction between a user and a film.
 *
 * <p>Append-only by design. A deliberately plain table rather than an event
 * bus: the engine only ever needs aggregate counts per (user, movie, type),
 * which a covering index answers in a single query. Introducing streaming
 * infrastructure here would be complexity without a corresponding benefit.
 */
@Entity
@Table(name = "watch_history")
public class WatchHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 32)
    private InteractionType interactionType;

    @Column(name = "progress_percent")
    private Short progressPercent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected WatchHistoryEntry() {
        // required by JPA
    }

    public WatchHistoryEntry(User user, Movie movie, InteractionType interactionType) {
        this.user = user;
        this.movie = movie;
        this.interactionType = interactionType;
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

    public InteractionType getInteractionType() {
        return interactionType;
    }

    public Short getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Short progressPercent) {
        if (progressPercent != null && (progressPercent < 0 || progressPercent > 100)) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100");
        }
        this.progressPercent = progressPercent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WatchHistoryEntry entry)) {
            return false;
        }
        return id != null && id.equals(entry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
