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

    List<VehicleSchedule> findByVehicle_IdAndStatus(Long vehicleId, ScheduleStatus status);
}
