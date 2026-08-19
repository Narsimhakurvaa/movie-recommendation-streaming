package com.cinevault.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/** A film genre. {@code tmdbId} links back to the external provider's taxonomy. */
@Entity
@Table(name = "genres")
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_id", unique = true)
    private Integer tmdbId;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    protected Genre() {
        // required by JPA
    }

    public Genre(Integer tmdbId, String name, String slug) {
        this.tmdbId = tmdbId;
        this.name = name;
        this.slug = slug;
    }

    public Long getId() {
        return id;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Genre genre)) {
            return false;
        }
        return id != null && id.equals(genre.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
