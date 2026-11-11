package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.CommissionStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Set;

public interface CommissionStatementLineRepository extends JpaRepository<CommissionStatementLine, Long> {

    @Query("select l.booking.id from CommissionStatementLine l where l.booking.id in :bookingIds")
    Set<Long> findBilledBookingIds(Collection<Long> bookingIds);
}
