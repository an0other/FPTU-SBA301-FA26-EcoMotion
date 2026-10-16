package com.fptufa26.ecomotion.schedule.repository;

import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VehicleScheduleRepository extends JpaRepository<VehicleSchedule, Long> {

    List<VehicleSchedule> findByBooking_Id(Long bookingId);

    @Query("""
            select count(s) > 0 from VehicleSchedule s
            where s.vehicle.id = :vehicleId
              and s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              and s.startAt < :endAt
              and s.endAt > :startAt
            """)
    boolean existsActiveOverlap(@Param("vehicleId") Long vehicleId,
                                @Param("startAt") Instant startAt,
                                @Param("endAt") Instant endAt);

    @Query("""
            select s from VehicleSchedule s
            where s.vehicle.id = :vehicleId
              and s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
              and s.startAt < :rangeTo
              and s.endAt > :rangeFrom
            order by s.startAt
            """)
    List<VehicleSchedule> findActiveInRange(@Param("vehicleId") Long vehicleId,
                                            @Param("rangeFrom") Instant rangeFrom,
                                            @Param("rangeTo") Instant rangeTo);

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
            Instant startAt);

    List<VehicleSchedule> findByVehicle_IdAndStatus(Long vehicleId, ScheduleStatus status);
    boolean existsByBooking_Renter_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long renterId,
            ScheduleStatus status,
            Instant endAt,
            Instant startAt);
}
