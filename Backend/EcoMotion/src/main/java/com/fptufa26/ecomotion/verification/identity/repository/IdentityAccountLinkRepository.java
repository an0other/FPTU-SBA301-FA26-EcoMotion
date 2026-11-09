package com.fptufa26.ecomotion.verification.identity.repository;

import com.fptufa26.ecomotion.verification.identity.entity.IdentityAccountLink;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface IdentityAccountLinkRepository extends JpaRepository<IdentityAccountLink, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from IdentityAccountLink l where l.id = :id")
    Optional<IdentityAccountLink> findForUpdate(@Param("id") UUID id);
    @Query("select l from IdentityAccountLink l where (l.sourceUserId = :userId or l.targetUserId = :userId) order by l.expiresAt desc")
    List<IdentityAccountLink> findVisible(@Param("userId") Long userId, org.springframework.data.domain.Pageable page);
}
