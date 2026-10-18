package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByVehicle_Provider_IdOrderByCreatedAtDesc(Long providerId);

    List<Booking> findByVehicle_Provider_IdAndStatusOrderByCreatedAtDesc(Long providerId, BookingStatus status);

    Optional<Booking> findByBookingCode(String bookingCode);

    Optional<Booking> findByIdAndVehicle_Provider_Id(Long id, Long providerId);

    Optional<Booking> findByBookingCodeAndVehicle_Provider_Id(String bookingCode, Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT b FROM Booking b " +
            "LEFT JOIN FETCH b.renter r " +
            "LEFT JOIN FETCH r.user ru " +
            "LEFT JOIN FETCH b.vehicle v " +
            "LEFT JOIN FETCH v.provider p " +
            "LEFT JOIN FETCH b.plan pl " +
            "LEFT JOIN FETCH b.driver d " +
            "LEFT JOIN FETCH d.user du " +
            "LEFT JOIN FETCH b.cancelledBy cb " +
            "WHERE b.id = :id AND p.id = :providerId")
    Optional<Booking> findDetailByIdAndProviderId(@Param("id") Long id, @Param("providerId") Long providerId);

    @Query("SELECT b FROM Booking b " +
            "LEFT JOIN FETCH b.renter r " +
            "LEFT JOIN FETCH r.user ru " +
            "LEFT JOIN FETCH b.vehicle v " +
            "LEFT JOIN FETCH v.provider p " +
            "LEFT JOIN FETCH b.plan pl " +
            "LEFT JOIN FETCH b.driver d " +
            "LEFT JOIN FETCH d.user du " +
            "LEFT JOIN FETCH b.cancelledBy cb " +
            "WHERE b.bookingCode = :bookingCode AND p.id = :providerId")
    Optional<Booking> findDetailByBookingCodeAndProviderId(@Param("bookingCode") String bookingCode, @Param("providerId") Long providerId);

    @Query("SELECT b FROM Booking b " +
            "JOIN FETCH b.plan pl " +
            "WHERE b.driver.id = :driverId " +
            "AND b.status IN :statuses " +
            "AND (:excludeBookingId IS NULL OR b.id <> :excludeBookingId) " +
            "AND b.pickupTime < :windowEnd")
    List<Booking> findPotentialDriverConflicts(
            @Param("driverId") Long driverId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("excludeBookingId") Long excludeBookingId,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("SELECT b FROM Booking b " +
            "JOIN FETCH b.plan pl " +
            "WHERE b.vehicle.id = :vehicleId " +
            "AND b.status IN :statuses " +
            "AND (:excludeBookingId IS NULL OR b.id <> :excludeBookingId) " +
            "AND b.pickupTime < :windowEnd")
    List<Booking> findPotentialVehicleConflicts(
            @Param("vehicleId") Long vehicleId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("excludeBookingId") Long excludeBookingId,
            @Param("windowEnd") Instant windowEnd
    );
}
