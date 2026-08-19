package com.cinevault.user.repository;

import com.cinevault.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Looks a token up by digest. Joins the user because the caller always
     * needs it to mint the next access token.
     */
    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHash(@Param("hash") String hash);

    /**
     * Revokes every live token for a user.
     *
     * <p>Used on logout-everywhere and, critically, on detection of a reused
     * token: if a revoked token is presented the family is assumed compromised
     * and all of its descendants are killed at once.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Housekeeping: drop rows that can no longer be used. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);

    long countByUserIdAndRevokedAtIsNull(Long userId);
}
