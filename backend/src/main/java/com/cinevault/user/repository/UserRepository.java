package com.cinevault.user.repository;

import com.cinevault.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Case-insensitive lookup backed by {@code idx_users_email_lower}.
     * Roles are fetched eagerly by the mapping, so this is a single query.
     */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    /** Admin listing with a free-text filter over name and email. */
    @Query("""
            select u from User u
            where (:search is null or :search = ''
                   or lower(u.displayName) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%')))
              and (:enabled is null or u.enabled = :enabled)
            """)
    Page<User> search(@Param("search") String search,
                      @Param("enabled") Boolean enabled,
                      Pageable pageable);

    /**
     * Records the login timestamp without loading the entity.
     * Avoids a read-modify-write cycle on a hot path.
     */
    @Modifying
    @Query("update User u set u.lastLoginAt = :timestamp where u.id = :userId")
    void recordLogin(@Param("userId") Long userId, @Param("timestamp") Instant timestamp);

    long countByEnabledTrue();

    long countByCreatedAtAfter(Instant threshold);
}
