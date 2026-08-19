package com.cinevault.catalogue.domain;

import com.cinevault.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A film in the catalogue.
 *
 * <h2>Fetching</h2>
 * <p>Every association is {@code LAZY}. Listing endpoints project straight to
 * DTOs and never touch them; the detail endpoint uses an explicit fetch join
 * (see {@code MovieRepository#findDetailById}). Marking any of these EAGER
 * would silently turn a 20-row listing into hundreds of queries.
 *
 * <h2>Denormalised aggregates</h2>
 * <p>{@code averageRating} and {@code ratingCount} are maintained by a database
 * trigger (migration V4). They are read-only from JPA's perspective - writing
 * them from Java would race with the trigger.
 */
@Entity
@Table(name = "movies")
public class Movie extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_id", unique = true)
    private Integer tmdbId;

    @Column(name = "imdb_id", length = 16)
    private String imdbId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(nullable = false, unique = true, length = 280)
    private String slug;

    @Column(length = 300)
    private String tagline;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "original_language", length = 12)
    private String originalLanguage;

    @Column(name = "origin_country", length = 64)
    private String originCountry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MovieStatus status = MovieStatus.RELEASED;

    @Column(name = "poster_url", length = 512)
    private String posterUrl;

    @Column(name = "backdrop_url", length = 512)
    private String backdropUrl;

    @Column(name = "trailer_url", length = 512)
    private String trailerUrl;

    @Column(name = "homepage_url", length = 512)
    private String homepageUrl;

    @Column(name = "external_rating", nullable = false, precision = 4, scale = 2)
    private BigDecimal externalRating = BigDecimal.ZERO;

    @Column(name = "external_vote_count", nullable = false)
    private int externalVoteCount;

    /** Maintained by database trigger; never written from the application. */
    @Column(name = "average_rating", nullable = false, precision = 4, scale = 2,
            insertable = false, updatable = false)
    private BigDecimal averageRating = BigDecimal.ZERO;

    /** Maintained by database trigger; never written from the application. */
    @Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
    private int ratingCount;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal popularity = BigDecimal.ZERO;

    private Long budget;

    private Long revenue;

    @Column(nullable = false)
    private boolean adult = false;

    @Column(name = "production_companies", length = 512)
    private String productionCompanies;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "movie_genres",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    private Set<Genre> genres = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "movie_keywords",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id"))
    private Set<Keyword> keywords = new LinkedHashSet<>();

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovieCredit> credits = new ArrayList<>();

    protected Movie() {
        // required by JPA
    }

    public Movie(String title, String slug) {
        this.title = title;
        this.slug = slug;
    }

    public Long getId() {
        return id;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Integer getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public void setRuntimeMinutes(Integer runtimeMinutes) {
        this.runtimeMinutes = runtimeMinutes;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public void setOriginalLanguage(String originalLanguage) {
        this.originalLanguage = originalLanguage;
    }

    public String getOriginCountry() {
        return originCountry;
    }

    public void setOriginCountry(String originCountry) {
        this.originCountry = originCountry;
    }

    public MovieStatus getStatus() {
        return status;
    }

    public void setStatus(MovieStatus status) {
        this.status = status;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getBackdropUrl() {
        return backdropUrl;
    }

    public void setBackdropUrl(String backdropUrl) {
        this.backdropUrl = backdropUrl;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public void setTrailerUrl(String trailerUrl) {
        this.trailerUrl = trailerUrl;
    }

    public String getHomepageUrl() {
        return homepageUrl;
    }

    public void setHomepageUrl(String homepageUrl) {
        this.homepageUrl = homepageUrl;
    }

    public BigDecimal getExternalRating() {
        return externalRating;
    }

    public void setExternalRating(BigDecimal externalRating) {
        this.externalRating = externalRating;
    }

    public int getExternalVoteCount() {
        return externalVoteCount;
    }

    public void setExternalVoteCount(int externalVoteCount) {
        this.externalVoteCount = externalVoteCount;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public BigDecimal getPopularity() {
        return popularity;
    }

    public void setPopularity(BigDecimal popularity) {
        this.popularity = popularity;
    }

    public Long getBudget() {
        return budget;
    }

    public void setBudget(Long budget) {
        this.budget = budget;
    }

    public Long getRevenue() {
        return revenue;
    }

    public void setRevenue(Long revenue) {
        this.revenue = revenue;
    }

    public boolean isAdult() {
        return adult;
    }

    public void setAdult(boolean adult) {
        this.adult = adult;
    }

    public String getProductionCompanies() {
        return productionCompanies;
    }

    public void setProductionCompanies(String productionCompanies) {
        this.productionCompanies = productionCompanies;
    }

    public Set<Genre> getGenres() {
        return genres;
    }

    public void addGenre(Genre genre) {
        genres.add(genre);
    }

    public Set<Keyword> getKeywords() {
        return keywords;
    }

    public void addKeyword(Keyword keyword) {
        keywords.add(keyword);
    }

    public List<MovieCredit> getCredits() {
        return credits;
    }

    /** Keeps both sides of the association consistent. */
    public void addCredit(MovieCredit credit) {
        credits.add(credit);
        credit.setMovie(this);
    }

    /** Convenience for the detail view; credits must already be initialised. */
    public List<MovieCredit> creditsOfType(CreditType type) {
        return credits.stream().filter(c -> c.getCreditType() == type).toList();
    }

    public Integer getReleaseYear() {
        return releaseDate == null ? null : releaseDate.getYear();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Movie movie)) {
            return false;
        }
        return id != null && id.equals(movie.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Movie[id=%s, title=%s]".formatted(id, title);
    }
}
