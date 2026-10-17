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

    List<VehicleSchedule> findByVehicle_IdOrderByStartAtAsc(Long vehicleId);

    List<VehicleSchedule> findByVehicle_IdAndStatusOrderByStartAtAsc(Long vehicleId, ScheduleStatus status);

    @Query("""
            SELECT COUNT(s) FROM VehicleSchedule s
            WHERE s.vehicle.provider.id = :providerId
              AND s.type IN ('BLOCKED', 'MAINTENANCE')
              AND s.status = com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.ACTIVE
            """)
    long countActiveBlocksByProviderId(@Param("providerId") Long providerId);

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
}
