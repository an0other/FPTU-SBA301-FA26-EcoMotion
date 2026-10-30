package com.fptufa26.ecomotion.schedule.repository;

import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VehicleScheduleRepository extends JpaRepository<VehicleSchedule, Long> {

    List<VehicleSchedule> findByBooking_Id(Long bookingId);

    List<VehicleSchedule> findByVehicle_IdOrderByStartAtAsc(Long vehicleId);

    List<VehicleSchedule> findByVehicle_IdAndStatusOrderByStartAtAsc(Long vehicleId, ScheduleStatus status);

    List<VehicleSchedule> findByVehicle_IdAndStatus(Long vehicleId, ScheduleStatus status);

    @Query("""
            SELECT COUNT(s) FROM VehicleSchedule s
            WHERE s.vehicle.provider.id = :providerId
              AND s.type IN ('BLOCKED', 'MAINTENANCE')
              AND (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
                   OR (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.HELD AND (s.expiresAt > CURRENT_TIMESTAMP or exists (select p.id from PaymentTransaction p where p.booking.id = s.booking.id and p.method = com.fptufa26.ecomotion.booking.enums.PaymentMethod.VNPAY and p.status = com.fptufa26.ecomotion.booking.enums.PaymentStatus.PENDING))))
              AND s.endAt > :now
            """)
    long countActiveBlocksByProviderId(@Param("providerId") Long providerId, @Param("now") Instant now);

    @Query("""
            SELECT COUNT(s) FROM VehicleSchedule s
            WHERE s.vehicle.provider.id = :providerId
              AND s.type = com.fptufa26.ecomotion.schedule.enums.ScheduleType.MAINTENANCE
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              AND s.endAt > :now
            """)
    long countActiveMaintenanceByProviderId(@Param("providerId") Long providerId, @Param("now") Instant now);

    @Query("""
            SELECT COUNT(s) FROM VehicleSchedule s
            WHERE s.vehicle.provider.id = :providerId
              AND s.type = com.fptufa26.ecomotion.schedule.enums.ScheduleType.BLOCKED
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              AND s.endAt > :now
            """)
    long countActiveBlockedByProviderId(@Param("providerId") Long providerId, @Param("now") Instant now);

    @Query("""
            SELECT COUNT(s) FROM VehicleSchedule s
            WHERE s.vehicle.provider.id = :providerId
              AND s.type = com.fptufa26.ecomotion.schedule.enums.ScheduleType.BOOKED
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              AND s.startAt >= :now
            """)
    long countUpcomingBookingsByProviderId(@Param("providerId") Long providerId, @Param("now") Instant now);

    @Query("""
            SELECT COUNT(DISTINCT s.vehicle.id) FROM VehicleSchedule s
            WHERE s.vehicle.provider.id = :providerId
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              AND s.type IN (com.fptufa26.ecomotion.schedule.enums.ScheduleType.BLOCKED, com.fptufa26.ecomotion.schedule.enums.ScheduleType.MAINTENANCE)
              AND s.startAt <= :now
              AND s.endAt > :now
            """)
    long countCurrentlyBlockedVehiclesByProviderId(@Param("providerId") Long providerId, @Param("now") Instant now);

    @Query("""
            SELECT s FROM VehicleSchedule s
            JOIN FETCH s.vehicle v
            LEFT JOIN FETCH s.booking b
            WHERE v.provider.id = :providerId
            ORDER BY s.startAt DESC
            """)
    List<VehicleSchedule> findByProviderIdOrderDesc(@Param("providerId") Long providerId);

    @Query("""
            SELECT COUNT(s) > 0 FROM VehicleSchedule s
            WHERE s.vehicle.id = :vehicleId
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              AND s.type = com.fptufa26.ecomotion.schedule.enums.ScheduleType.BOOKED
              AND s.startAt < :endAt
              AND s.endAt > :startAt
            """)
    boolean existsActiveBookingOverlap(
            @Param("vehicleId") Long vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            SELECT COUNT(s) > 0 FROM VehicleSchedule s
            WHERE s.vehicle.id = :vehicleId
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              AND s.type IN (com.fptufa26.ecomotion.schedule.enums.ScheduleType.BLOCKED, com.fptufa26.ecomotion.schedule.enums.ScheduleType.MAINTENANCE)
              AND s.startAt < :endAt
              AND s.endAt > :startAt
            """)
    boolean existsActiveBlockOverlap(
            @Param("vehicleId") Long vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select count(s) > 0 from VehicleSchedule s
            where s.vehicle.id = :vehicleId
              and (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
                   or (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.HELD and (s.expiresAt > CURRENT_TIMESTAMP or exists (select p.id from PaymentTransaction p where p.booking.id = s.booking.id and p.method = com.fptufa26.ecomotion.booking.enums.PaymentMethod.VNPAY and p.status = com.fptufa26.ecomotion.booking.enums.PaymentStatus.PENDING))))
              and s.startAt < :endAt
              and s.endAt > :startAt
            """)
    boolean existsActiveOverlap(
            @Param("vehicleId") Long vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select s from VehicleSchedule s
            where s.vehicle.id = :vehicleId
              and s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              and s.startAt < :rangeTo
              and s.endAt > :rangeFrom
            order by s.startAt
            """)
    List<VehicleSchedule> findActiveInRange(
            @Param("vehicleId") Long vehicleId,
            @Param("rangeFrom") Instant rangeFrom,
            @Param("rangeTo") Instant rangeTo
    );

    @Query("""
            select s from VehicleSchedule s
            where s.vehicle.id = :vehicleId
              and (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
                   or (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.HELD
                       and (s.expiresAt > CURRENT_TIMESTAMP or exists (select p.id from PaymentTransaction p where p.booking.id = s.booking.id and p.method = com.fptufa26.ecomotion.booking.enums.PaymentMethod.VNPAY and p.status = com.fptufa26.ecomotion.booking.enums.PaymentStatus.PENDING))))
              and s.startAt < :rangeTo
              and s.endAt > :rangeFrom
            order by s.startAt
            """)
    List<VehicleSchedule> findOccupiedInRange(
            @Param("vehicleId") Long vehicleId,
            @Param("rangeFrom") Instant rangeFrom,
            @Param("rangeTo") Instant rangeTo
    );

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM VehicleSchedule s
            WHERE s.vehicle.id = :vehicleId
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              AND s.startAt < :endAt
              AND s.endAt > :startAt
            """)
    boolean existsOverlappingSchedule(
            @Param("vehicleId") Long vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select count(s) > 0 from VehicleSchedule s
            where s.vehicle.id = :vehicleId
              and (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
                   or (s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.HELD
                       and (s.expiresAt > CURRENT_TIMESTAMP or exists (select p.id from PaymentTransaction p where p.booking.id = s.booking.id and p.method = com.fptufa26.ecomotion.booking.enums.PaymentMethod.VNPAY and p.status = com.fptufa26.ecomotion.booking.enums.PaymentStatus.PENDING))))
              and (s.booking is null or s.booking.id <> :excludeBookingId)
              and s.startAt < :endAt
              and s.endAt > :startAt
            """)
    boolean existsOccupiedOverlapExcludingBooking(
            @Param("vehicleId") Long vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeBookingId") Long excludeBookingId);

    @Query("SELECT COUNT(s) > 0 FROM VehicleSchedule s " +
            "WHERE s.vehicle.id = :vehicleId " +
            "AND s.status = :status " +
            "AND (:excludeBookingId IS NULL OR s.booking IS NULL OR s.booking.id <> :excludeBookingId) " +
            "AND s.startAt < :endAt " +
            "AND s.endAt > :startAt")
    boolean existsOverlappingSchedule(
            @Param("vehicleId") Long vehicleId,
            @Param("status") ScheduleStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeBookingId") Long excludeBookingId
    );

    boolean existsByVehicle_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long vehicleId,
            ScheduleStatus status,
            Instant endAt,
            Instant startAt
    );

    boolean existsByBooking_Renter_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long renterId,
            ScheduleStatus status,
            Instant endAt,
            Instant startAt
    );

    @Query("""
            select distinct s.booking.id from VehicleSchedule s
            where s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.HELD
              and s.expiresAt <= :now
              and s.booking is not null
            """)
    List<Long> findExpiredHoldBookingIds(@Param("now") Instant now);
}
