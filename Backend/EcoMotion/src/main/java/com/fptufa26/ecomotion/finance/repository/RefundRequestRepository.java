package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    List<RefundRequest> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    List<RefundRequest> findByBooking_Vehicle_Provider_IdOrderByCreatedAtDesc(Long providerId);

    List<RefundRequest> findByStatus(RefundStatus status);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundRequest r WHERE r.booking.id = :bookingId AND r.status IN ('APPROVED', 'PROCESSING', 'REFUNDED')")
    BigDecimal sumCommittedRefundsForBooking(@Param("bookingId") Long bookingId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundRequest r WHERE r.booking.vehicle.provider.id = :providerId AND r.status = 'REFUNDED'")
    BigDecimal sumCompletedRefundsForProvider(@Param("providerId") Long providerId);

    boolean existsByBooking_IdAndStatusIn(Long bookingId, List<RefundStatus> statuses);
}
