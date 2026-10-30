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
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    @EntityGraph(attributePaths = "booking")
    List<PaymentTransaction> findByMethodAndStatusAndVnpTxnRefIsNotNullOrderByUpdatedAtAscCreatedAtAsc(
            com.fptufa26.ecomotion.booking.enums.PaymentMethod method, PaymentStatus status, Pageable pageable);

    List<PaymentTransaction> findAllByOrderByCreatedAtDesc();

    List<PaymentTransaction> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<PaymentTransaction> findByTypeOrderByCreatedAtDesc(PaymentType type);

    List<PaymentTransaction> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    List<PaymentTransaction> findByBooking_IdIn(Collection<Long> bookingIds);

    long countByStatus(PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p WHERE p.status = 'PAID' AND p.direction = 'IN'")
    BigDecimal sumTotalPaidIn();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentTransaction p JOIN p.booking b WHERE p.status = com.fptufa26.ecomotion.booking.enums.PaymentStatus.PAID AND p.type = com.fptufa26.ecomotion.booking.enums.PaymentType.DEPOSIT_PAY AND p.direction = com.fptufa26.ecomotion.booking.enums.PaymentDirection.IN AND b.status IN (com.fptufa26.ecomotion.booking.enums.BookingStatus.CONFIRMED, com.fptufa26.ecomotion.booking.enums.BookingStatus.DRIVER_ASSIGNED, com.fptufa26.ecomotion.booking.enums.BookingStatus.DRIVER_CONFIRMED, com.fptufa26.ecomotion.booking.enums.BookingStatus.DRIVER_ARRIVED, com.fptufa26.ecomotion.booking.enums.BookingStatus.IN_PROGRESS, com.fptufa26.ecomotion.booking.enums.BookingStatus.AWAITING_END_CONFIRMATION, com.fptufa26.ecomotion.booking.enums.BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION)")
    BigDecimal sumEscrowDeposits();

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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.vnpTxnRef = :ref")
    Optional<PaymentTransaction> findByVnpTxnRefForUpdate(@Param("ref") String ref);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

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
