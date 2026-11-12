package com.fptufa26.ecomotion.verification.progress;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface VerificationPipelineRunRepository extends JpaRepository<VerificationPipelineRun,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from VerificationPipelineRun r where r.id=:id and r.ownerUserId=:owner")
    Optional<VerificationPipelineRun> lockOwned(@Param("owner") Long owner,@Param("id") UUID id);
    Optional<VerificationPipelineRun> findByIdAndOwnerUserId(UUID id,Long owner);
    List<VerificationPipelineRun> findByOwnerUserIdAndAttemptIdOrderByStartedAtAsc(Long owner,UUID attempt);
    List<VerificationPipelineRun> findByOwnerUserIdAndKindOrderByStartedAtDesc(Long owner,String kind,Pageable pageable);
}
