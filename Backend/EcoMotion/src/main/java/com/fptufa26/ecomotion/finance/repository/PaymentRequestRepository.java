package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    @EntityGraph(attributePaths = {"booking", "booking.renter", "booking.renter.user", "evidences"})
    List<PaymentRequest> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.renter", "booking.renter.user", "evidences"})
    Optional<PaymentRequest> findByBooking_IdAndPaymentType(Long bookingId, PaymentRequestType paymentType);

    @EntityGraph(attributePaths = {"booking", "booking.renter", "booking.renter.user", "evidences"})
    Optional<PaymentRequest> findByTransferReference(String transferReference);

    @EntityGraph(attributePaths = {"booking", "booking.vehicle", "booking.vehicle.provider", "booking.renter", "booking.renter.user", "evidences"})
    Optional<PaymentRequest> findByIdAndBooking_Vehicle_Provider_Id(Long id, Long providerId);

    @EntityGraph(attributePaths = {"booking", "booking.vehicle", "booking.renter", "booking.renter.user", "evidences"})
    Page<PaymentRequest> findByBooking_Vehicle_Provider_Id(Long providerId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.vehicle", "booking.renter", "booking.renter.user", "evidences"})
    Page<PaymentRequest> findByBooking_Vehicle_Provider_IdAndStatus(Long providerId, PaymentRequestStatus status, Pageable pageable);

    boolean existsByBooking_IdAndPaymentTypeAndStatusIn(Long bookingId, PaymentRequestType paymentType, Collection<PaymentRequestStatus> statuses);

    long countByBooking_Vehicle_Provider_IdAndStatus(Long providerId, PaymentRequestStatus status);

    long countByBooking_Vehicle_Provider_Id(Long providerId);
}
