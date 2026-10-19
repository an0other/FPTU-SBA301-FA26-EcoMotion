package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByBooking_IdOrderByCreatedAtAsc(Long bookingId);

    List<PaymentTransaction> findByBooking_Id(Long bookingId);

    boolean existsByBooking_IdAndType(Long bookingId, PaymentType type);

    Optional<PaymentTransaction> findByBooking_IdAndType(Long bookingId, PaymentType type);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    Page<PaymentTransaction> findByBooking_Vehicle_Provider_IdAndType(Long providerId, PaymentType type,
            Pageable pageable);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    Page<PaymentTransaction> findByBooking_Vehicle_Provider_IdAndTypeAndStatus(Long providerId, PaymentType type,
            PaymentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    Optional<PaymentTransaction> findByIdAndBooking_Vehicle_Provider_Id(Long id, Long providerId);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    List<PaymentTransaction> findByBooking_Vehicle_Provider_IdAndType(Long providerId, PaymentType type);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    Page<PaymentTransaction> findByBooking_Vehicle_Provider_Id(Long providerId, Pageable pageable);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    List<PaymentTransaction> findByBooking_Vehicle_Provider_Id(Long providerId);

    Optional<PaymentTransaction> findByGatewayRef(String gatewayRef);

    boolean existsByGatewayRef(String gatewayRef);
}
