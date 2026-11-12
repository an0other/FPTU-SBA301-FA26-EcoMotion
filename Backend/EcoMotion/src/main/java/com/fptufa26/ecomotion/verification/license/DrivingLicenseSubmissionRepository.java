package com.fptufa26.ecomotion.verification.license;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;

public interface DrivingLicenseSubmissionRepository extends JpaRepository<DrivingLicenseSubmission, Long> {
    Optional<DrivingLicenseSubmission> findFirstByRenter_IdOrderByIdDesc(Long renterId);
    boolean existsByRenter_IdAndStatusIn(Long renterId, Collection<String> statuses);
    Page<DrivingLicenseSubmission> findByRenter_Id(Long renterId, Pageable page);
    Page<DrivingLicenseSubmission> findByStatus(String status, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DrivingLicenseSubmission s where s.publicId = :id")
    Optional<DrivingLicenseSubmission> lock(@Param("id") UUID id);
}
