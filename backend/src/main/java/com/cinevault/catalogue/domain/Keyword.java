package com.cinevault.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * A thematic tag such as "time travel" or "heist".
 *
 * <p>Keywords carry most of the signal that genre alone cannot express, and are
 * what let the content-based strategy tell a cerebral science-fiction drama
 * apart from a space adventure.
 */
@Entity
@Table(name = "keywords")
public class Keyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 96)
    private String name;

    protected Keyword() {
        // required by JPA
    }

    public Keyword(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Keyword keyword)) {
            return false;
        }
        return id != null && id.equals(keyword.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
