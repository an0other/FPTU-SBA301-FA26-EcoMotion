package com.fptufa26.ecomotion.verification.identity.repository;

import com.fptufa26.ecomotion.verification.identity.entity.IdentityVerificationAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdentityVerificationAttemptRepository extends JpaRepository<IdentityVerificationAttempt, Long> {
    org.springframework.data.domain.Page<IdentityVerificationAttempt> findByUser_IdOrderByIdDesc(
            Long userId, org.springframework.data.domain.Pageable pageable);
    boolean existsByActiveUserId(Long userId);
    Optional<IdentityVerificationAttempt> findFirstByUser_IdOrderByIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from IdentityVerificationAttempt a where a.publicId = :publicId")
    Optional<IdentityVerificationAttempt> findPublicForUpdate(@Param("publicId") UUID publicId);

    org.springframework.data.domain.Page<IdentityVerificationAttempt> findByStatusAndRole(
            com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus status,
            com.fptufa26.ecomotion.user.enums.Role role, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from IdentityVerificationAttempt a where a.publicId = :publicId and a.user.id = :userId")
    Optional<IdentityVerificationAttempt> findOwnedForUpdate(
            @Param("publicId") UUID publicId, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from IdentityVerificationAttempt a where a.activeUserId = :userId")
    Optional<IdentityVerificationAttempt> findActiveForUpdate(@Param("userId") Long userId);
}
