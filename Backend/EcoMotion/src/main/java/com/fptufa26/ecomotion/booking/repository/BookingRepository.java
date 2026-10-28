package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    List<Booking> findByRenter_IdOrderByCreatedAtDesc(Long renterId);

    List<Booking> findByVehicle_IdOrderByCreatedAtDesc(Long vehicleId);

    long countByStatus(BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.totalAmount * p.commissionRate), 0) FROM Booking b JOIN b.vehicle v JOIN v.provider p WHERE b.status = com.fptufa26.ecomotion.booking.enums.BookingStatus.COMPLETED")
    BigDecimal sumAdminCommission();

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.status = com.fptufa26.ecomotion.booking.enums.BookingStatus.COMPLETED")
    BigDecimal sumCompletedGrossRentalVolume();

    @Override
    @EntityGraph(attributePaths = {
            "vehicle", "renter", "renter.user", "driver", "driver.user", "plan"
    })
    List<Booking> findAll(@Nullable Specification<Booking> spec);

    @Override
    @EntityGraph(attributePaths = {
            "vehicle", "renter", "renter.user", "driver", "driver.user", "plan"
    })
    List<Booking> findAll(@Nullable Specification<Booking> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {
            "vehicle", "renter", "renter.user", "driver", "driver.user", "plan"
    })
    Page<Booking> findAll(@Nullable Specification<Booking> spec, Pageable pageable);

    Optional<Booking> findByBookingCodeAndRenter_User_Id(
            String bookingCode,
            Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingCode = :bookingCode AND b.renter.user.id = :userId")
    Optional<Booking> findByBookingCodeAndRenterUserIdForUpdate(
            @Param("bookingCode") String bookingCode, @Param("userId") Long userId);
    // Existing renter and booking-code queries.
    long countByBookingCodeStartingWith(String prefix);

    @EntityGraph(attributePaths = {
            "vehicle", "renter", "renter.user", "driver", "driver.user", "plan"
    })
    Optional<Booking> findByBookingCode(String bookingCode);

    boolean existsByBookingCode(String bookingCode);

    List<Booking> findByRenter_IdAndStatusIn(Long renterId, Collection<BookingStatus> statuses);

    List<Booking> findByRenter_IdOrderByPickupTimeDesc(Long renterId);

    List<Booking> findByRenter_IdAndStatusOrderByPickupTimeDesc(Long renterId, BookingStatus status);

    List<Booking> findByVehicle_IdAndStatusIn(Long vehicleId, Collection<BookingStatus> statuses);

    // Provider booking queue and ownership-scoped lookups.
    List<Booking> findByVehicle_Provider_IdOrderByCreatedAtDesc(Long providerId);

    List<Booking> findByVehicle_Provider_IdAndStatusOrderByCreatedAtDesc(Long providerId, BookingStatus status);

    @EntityGraph(attributePaths = {
            "vehicle", "renter", "renter.user", "driver", "driver.user", "plan"
    })
    Optional<Booking> findByIdAndVehicle_Provider_Id(Long id, Long providerId);

    @EntityGraph(attributePaths = {
            "vehicle",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    Optional<Booking> findByBookingCodeAndVehicle_Provider_Id(String bookingCode, Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id AND b.vehicle.provider.id = :providerId")
    Optional<Booking> findByIdAndProviderIdForUpdate(@Param("id") Long id, @Param("providerId") Long providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingCode = :bookingCode AND b.vehicle.provider.id = :providerId")
    Optional<Booking> findByBookingCodeAndProviderIdForUpdate(
            @Param("bookingCode") String bookingCode, @Param("providerId") Long providerId);

    List<Booking> findByVehicle_Provider_Id(Long providerId);

    Page<Booking> findByVehicle_Provider_Id(Long providerId, Pageable pageable);

    long countByVehicle_Provider_Id(Long providerId);

    long countByVehicle_Provider_IdAndStatus(Long providerId, BookingStatus status);

    long countByVehicle_Provider_IdAndStatusIn(Long providerId, Collection<BookingStatus> statuses);

    long countByDriver_IdAndStatusIn(Long driverId, Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {
            "vehicle", "vehicle.provider", "vehicle.provider.user",
            "renter", "renter.user", "driver", "driver.user", "plan"
    })
    Optional<Booking> findByIdAndDriver_Id(Long id, Long driverId);

    @EntityGraph(attributePaths = {
            "vehicle", "vehicle.provider", "vehicle.provider.user",
            "renter", "renter.user", "driver", "driver.user", "plan"
    })
    Optional<Booking> findByBookingCodeAndDriver_Id(String bookingCode, Long driverId);

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
    Optional<Booking> findDetailByBookingCodeAndProviderId(@Param("bookingCode") String bookingCode,
                                                            @Param("providerId") Long providerId);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findBookingByBookingCodeAndRenter_User_Id(
            String bookingCode,
            Long userId);

    @Query("SELECT b FROM Booking b WHERE b.driver.id = :driverId AND b.status IN :statuses " +
            "AND b.pickupTime < :windowEnd AND (b.actualEndAt IS NULL OR b.actualEndAt > :windowStart)")
    List<Booking> findOverlappingDriverBookings(
            @Param("driverId") Long driverId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.driver.id = :driverId AND b.status IN :statuses " +
            "AND b.pickupTime >= :start AND b.pickupTime < :end")
    long countDriverTripsBetween(
            @Param("driverId") Long driverId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    boolean existsByDriver_IdAndStatus(Long driverId, BookingStatus status);

    Optional<Booking> findFirstByDriver_IdAndStatus(Long driverId, BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.driver.id = :driverId AND b.status IN :statuses " +
            "AND (b.pickupTime >= :now OR b.status IN ('DRIVER_ASSIGNED', 'DRIVER_CONFIRMED', 'DRIVER_ARRIVED')) " +
            "ORDER BY b.pickupTime ASC")
    List<Booking> findUpcomingDriverBookings(
            @Param("driverId") Long driverId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("now") Instant now
    );
}
