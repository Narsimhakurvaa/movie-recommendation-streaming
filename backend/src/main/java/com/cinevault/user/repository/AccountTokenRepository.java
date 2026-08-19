package com.cinevault.user.repository;

import com.cinevault.user.domain.AccountToken;
import com.cinevault.user.domain.AccountTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    @Query("select t from AccountToken t join fetch t.user where t.tokenHash = :hash and t.tokenType = :type")
    Optional<AccountToken> findUsable(@Param("hash") String hash, @Param("type") AccountTokenType type);

    /** Invalidates outstanding tokens so only the newest request is valid. */
    @Modifying
    @Query("""
           update AccountToken t set t.consumedAt = :now
           where t.user.id = :userId and t.tokenType = :type and t.consumedAt is null
           """)
    int consumeOutstanding(@Param("userId") Long userId,
                           @Param("type") AccountTokenType type,
                           @Param("now") Instant now);

    @Modifying
    @Query("delete from AccountToken t where t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
