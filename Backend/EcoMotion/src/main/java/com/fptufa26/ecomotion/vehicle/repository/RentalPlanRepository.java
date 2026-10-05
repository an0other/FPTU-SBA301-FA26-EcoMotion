package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.PlanStatus;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RentalPlanRepository extends JpaRepository<RentalPlan, Long> {

    List<RentalPlan> findByVehicle_IdAndStatusOrderByIdAsc(Long vehicleId, PlanStatus status);

    Optional<RentalPlan> findByIdAndVehicle_Id(Long id, Long vehicleId);

    boolean existsByVehicle_Id(Long vehicleId);
}
