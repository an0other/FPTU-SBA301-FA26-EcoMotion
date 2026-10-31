package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.DriverAssignment;
import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriverAssignmentRepository extends JpaRepository<DriverAssignment, Long> {

    List<DriverAssignment> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    Optional<DriverAssignment> findFirstByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    Optional<DriverAssignment> findByBooking_IdAndDriver_IdAndStatus(Long bookingId, Long driverId, AssignmentStatus status);

    List<DriverAssignment> findByDriver_IdAndStatus(Long driverId, AssignmentStatus status);

    @Query("SELECT da.driver.id FROM DriverAssignment da WHERE da.booking.id = :bookingId AND da.status IN ('REJECTED', 'TIMEOUT')")
    List<Long> findRejectedDriverIdsForBooking(@Param("bookingId") Long bookingId);

    @Query("SELECT da FROM DriverAssignment da WHERE da.status = 'PENDING_RESPONSE' AND da.expiresAt IS NOT NULL AND da.expiresAt <= :now")
    List<DriverAssignment> findExpiredAssignments(@Param("now") Instant now);

    @Query("select distinct da.booking.id from DriverAssignment da where da.status = 'PENDING_RESPONSE' and da.expiresAt <= :now")
    List<Long> findExpiredBookingIds(@Param("now") Instant now);

    boolean existsByBooking_IdAndDriver_IdAndStatus(Long bookingId, Long driverId, AssignmentStatus status);
}
