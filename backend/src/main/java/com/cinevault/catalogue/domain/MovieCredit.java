package com.cinevault.catalogue.domain;

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

import java.util.Objects;

/**
 * Links a {@link Person} to a {@link Movie} in a specific capacity.
 *
 * <p>Cast and crew share one table with a discriminator rather than being split
 * apart: the columns are identical and every read path filters by movie, so
 * separate tables would add joins for no benefit.
 */
@Entity
@Table(name = "movie_credits")
public class MovieCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, length = 16)
    private CreditType creditType;

    @Column(name = "character_name", length = 160)
    private String characterName;

    @Column(length = 96)
    private String job;

    /** Billing order; lower values are listed first. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected MovieCredit() {
        // required by JPA
    }

    public MovieCredit(Person person, CreditType creditType, int displayOrder) {
        this.person = person;
        this.creditType = creditType;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    void setMovie(Movie movie) {
        this.movie = movie;
    }

    public Person getPerson() {
        return person;
    }

    public CreditType getCreditType() {
        return creditType;
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MovieCredit credit)) {
            return false;
        }
        return id != null && id.equals(credit.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
