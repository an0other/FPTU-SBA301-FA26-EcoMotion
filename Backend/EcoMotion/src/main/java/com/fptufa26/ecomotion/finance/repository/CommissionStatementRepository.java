package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.CommissionStatement;
import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommissionStatementRepository extends JpaRepository<CommissionStatement, Long> {

    Optional<CommissionStatement> findByProvider_IdAndPeriodMonth(Long providerId, String periodMonth);

    List<CommissionStatement> findByProvider_IdOrderByPeriodMonthDesc(Long providerId);

    List<CommissionStatement> findByPeriodMonthOrderByIdAsc(String periodMonth);

    List<CommissionStatement> findAllByOrderByPeriodMonthDescIdAsc();

    List<CommissionStatement> findByStatusInAndDueAtBefore(Collection<CommissionStatementStatus> statuses, Instant dueAt);

    @Query("select distinct s.provider.id from CommissionStatement s where s.status in :statuses and s.dueAt < :cutoff")
    List<Long> findProvidersOverdueBefore(@Param("statuses") Collection<CommissionStatementStatus> statuses,
                                          @Param("cutoff") Instant cutoff);
}
