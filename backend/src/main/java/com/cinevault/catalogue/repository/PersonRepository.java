package com.cinevault.catalogue.repository;

import com.cinevault.catalogue.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByNameIgnoreCase(String name);

    List<Person> findByIdIn(Collection<Long> ids);
}
