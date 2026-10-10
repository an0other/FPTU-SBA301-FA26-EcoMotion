package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface RentalPlanRepository extends JpaRepository<RentalPlan, Long> {

    List<RentalPlan> findByVehicle_IdOrderByIdAsc(Long vehicleId);

    List<RentalPlan> findByVehicle_IdAndStatusOrderByIdAsc(Long vehicleId, PlanStatus status);

    List<RentalPlan> findByVehicle_IdInAndStatusOrderByIdAsc(Collection<Long> vehicleIds, PlanStatus status);
}
