package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findAllByOrderByCreatedAtDesc();

    List<PaymentTransaction> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<PaymentTransaction> findByTypeOrderByCreatedAtDesc(PaymentType type);

    List<PaymentTransaction> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p WHERE p.status = 'PAID' AND p.direction = 'IN'")
    BigDecimal sumTotalPaidIn();

    List<PaymentTransaction> findByBooking_IdOrderByCreatedAtAsc(Long bookingId);

    List<PaymentTransaction> findByBooking_Id(Long bookingId);

    boolean existsByBooking_IdAndType(Long bookingId, PaymentType type);

    Optional<PaymentTransaction> findByBooking_IdAndType(Long bookingId, PaymentType type);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    Page<PaymentTransaction> findByBooking_Vehicle_Provider_IdAndType(Long providerId, PaymentType type,
                                                                       Pageable pageable);

    @EntityGraph(attributePaths = { "booking", "booking.vehicle", "booking.vehicle.provider" })
    Page<PaymentTransaction> findByBooking_Vehicle_Provider_IdAndTypeAndStatus(Long providerId, PaymentType type,
                                                                                PaymentStatus status,
                                                                                Pageable pageable);

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

boolean existsByReceivingBankAccount_IdAndGatewayRef(Long receivingBankAccountId, String gatewayRef);

    Optional<PaymentTransaction> findByVnpTxnRef(String vnpTxnRef);

    boolean existsByVnpTxnRef(String vnpTxnRef);

    boolean existsByCharge_IdAndStatus(Long chargeId, PaymentStatus status);
    @EntityGraph(attributePaths = "booking")
    Page<PaymentTransaction>
    findByBooking_Renter_User_IdAndTypeNotOrderByCreatedAtDescIdDesc(
            Long userId,
            PaymentType excludedType,
            Pageable pageable);

    @EntityGraph(attributePaths = "booking")
    List<PaymentTransaction> findByBooking_IdAndTypeNotOrderByCreatedAtDescIdDesc(
            Long bookingId,
            PaymentType excludedType);


}
