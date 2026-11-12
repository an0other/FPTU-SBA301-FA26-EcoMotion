package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.CommissionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommissionPaymentRepository extends JpaRepository<CommissionPayment, Long> {

    Optional<CommissionPayment> findFirstByStatement_IdAndStatusOrderByReportedAtDesc(Long statementId, String status);

    boolean existsByStatement_Id(Long statementId);
}
