package com.fptufa26.ecomotion.schedule.repository;

import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface VehicleScheduleRepository
        extends JpaRepository<VehicleSchedule, Long> {

    boolean existsByVehicle_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long vehicleId,
            ScheduleStatus status,
            Instant endAt,
            Instant startAt);

    boolean existsByBooking_Renter_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
            Long renterId,
            ScheduleStatus status,
            Instant endAt,
            Instant startAt);
}