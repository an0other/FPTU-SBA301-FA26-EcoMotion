package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.FinancialReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FinancialReviewRecordRepository extends JpaRepository<FinancialReviewRecord, Long> {
    Optional<FinancialReviewRecord> findByTargetTypeAndTargetIdAndRequestId(String targetType, Long targetId, String requestId);
    List<FinancialReviewRecord> findByTargetTypeAndTargetIdOrderByIdAsc(String targetType, Long targetId);
}
