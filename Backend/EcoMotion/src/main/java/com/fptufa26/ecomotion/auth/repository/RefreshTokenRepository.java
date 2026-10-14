package com.fptufa26.ecomotion.auth.repository;

import com.fptufa26.ecomotion.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken rt
               set rt.revokedAt = :revokedAt
             where rt.familyId = :familyId
               and rt.revokedAt is null
            """)
    int revokeActiveByFamilyId(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken rt
               set rt.revokedAt = :revokedAt
             where rt.user.id = :userId
               and rt.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken rt where rt.expiresAt < :before")
    int deleteAllExpiredBefore(@Param("before") Instant before);
}
