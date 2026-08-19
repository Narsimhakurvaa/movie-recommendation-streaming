package com.cinevault.interaction.domain;

import com.cinevault.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Per-user settings that shape both the UI and the recommendation filters.
 *
 * <p>Shares its primary key with {@code users} via {@link MapsId}, so the
 * relationship is genuinely one-to-one at the database level rather than merely
 * by convention.
 *
 * <p>{@code preferredLanguages} is stored as a short comma-separated string.
 * A join table would be more "correct", but this value is only ever read and
 * written whole, is capped at a handful of entries, and is never queried by
 * element - so the extra table would buy nothing.
 */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "preferred_languages", length = 128)
    private String preferredLanguages;

    @Column(name = "include_adult", nullable = false)
    private boolean includeAdult = false;

    @Column(name = "minimum_rating", nullable = false, precision = 4, scale = 2)
    private BigDecimal minimumRating = BigDecimal.ZERO;

    @Column(name = "preferred_decade_from")
    private Short preferredDecadeFrom;

    @Column(name = "preferred_decade_to")
    private Short preferredDecadeTo;

    @Column(nullable = false, length = 16)
    private String theme = "system";

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPreferences() {
        // required by JPA
    }

    public UserPreferences(User user) {
        this.user = user;
        this.userId = user.getId();
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public String getPreferredLanguages() {
        return preferredLanguages;
    }

    public void setPreferredLanguages(String preferredLanguages) {
        this.preferredLanguages = preferredLanguages;
    }

    /** Parsed view of {@link #getPreferredLanguages()}. */
    public List<String> preferredLanguageCodes() {
        if (preferredLanguages == null || preferredLanguages.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(preferredLanguages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void setPreferredLanguageCodes(List<String> codes) {
        this.preferredLanguages = (codes == null || codes.isEmpty()) ? null : String.join(",", codes);
    }

    public boolean isIncludeAdult() {
        return includeAdult;
    }

    public void setIncludeAdult(boolean includeAdult) {
        this.includeAdult = includeAdult;
    }

    public BigDecimal getMinimumRating() {
        return minimumRating;
    }

    public void setMinimumRating(BigDecimal minimumRating) {
        this.minimumRating = minimumRating;
    }

    public Short getPreferredDecadeFrom() {
        return preferredDecadeFrom;
    }

    public void setPreferredDecadeFrom(Short preferredDecadeFrom) {
        this.preferredDecadeFrom = preferredDecadeFrom;
    }

    public Short getPreferredDecadeTo() {
        return preferredDecadeTo;
    }

    public void setPreferredDecadeTo(Short preferredDecadeTo) {
        this.preferredDecadeTo = preferredDecadeTo;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isEmailNotifications() {
        return emailNotifications;
    }

    public void setEmailNotifications(boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserPreferences prefs)) {
            return false;
        }
        return userId != null && userId.equals(prefs.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
