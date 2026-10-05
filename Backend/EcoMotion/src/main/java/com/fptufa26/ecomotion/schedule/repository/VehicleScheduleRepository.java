package com.fptufa26.ecomotion.schedule.repository;

import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface VehicleScheduleRepository extends JpaRepository<VehicleSchedule, Long> {

    @Query("""
            select count(s) > 0 from VehicleSchedule s
            where s.vehicle.id = :vehicleId
              and s.status = com.fptufa26.ecomotion.schedule.entity.ScheduleStatus.ACTIVE
              and s.startAt < :endAt
              and s.endAt > :startAt
            """)
    boolean existsActiveOverlap(@Param("vehicleId") Long vehicleId,
                                @Param("startAt") Instant startAt,
                                @Param("endAt") Instant endAt);
}
