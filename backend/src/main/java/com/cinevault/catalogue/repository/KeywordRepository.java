package com.cinevault.catalogue.repository;

import com.cinevault.catalogue.domain.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    Optional<Keyword> findByNameIgnoreCase(String name);

    List<Keyword> findByIdIn(Collection<Long> ids);
}
