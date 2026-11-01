package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") Long id);

    List<RefundRequest> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    Optional<RefundRequest> findByPaymentTransaction_Id(Long paymentTransactionId);

    List<RefundRequest> findByBooking_Vehicle_Provider_IdOrderByCreatedAtDesc(Long providerId);

    List<RefundRequest> findByStatus(RefundStatus status);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundRequest r WHERE r.booking.id = :bookingId AND r.status IN ('REQUESTED', 'UNDER_REVIEW', 'APPROVED', 'PROCESSING', 'PENDING_RECONCILIATION', 'REFUNDED')")
    BigDecimal sumCommittedRefundsForBooking(@Param("bookingId") Long bookingId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundRequest r WHERE r.booking.vehicle.provider.id = :providerId AND r.status = 'REFUNDED'")
    BigDecimal sumCompletedRefundsForProvider(@Param("providerId") Long providerId);

    boolean existsByBooking_IdAndStatusIn(Long bookingId, List<RefundStatus> statuses);
}
