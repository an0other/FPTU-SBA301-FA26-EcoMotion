package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.vehicle.dto.RentalPlanData;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.service.RentalPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentalPlanServiceImpl implements RentalPlanService {

    private final RentalPlanRepository rentalPlanRepository;

    @Override
    public List<RentalPlanData> findActivePlans(Long vehicleId) {
        return rentalPlanRepository.findByVehicle_IdAndStatusOrderByIdAsc(vehicleId, PlanStatus.ACTIVE)
                .stream()
                .map(this::toData)
                .toList();
    }

    @Override
    public Map<Long, List<RentalPlanData>> findActivePlansByVehicleIds(Collection<Long> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }
        return rentalPlanRepository.findByVehicle_IdInAndStatusOrderByIdAsc(vehicleIds, PlanStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(plan -> plan.getVehicle().getId(),
                        Collectors.mapping(this::toData, Collectors.toList())));
    }

    private RentalPlanData toData(RentalPlan plan) {
        return new RentalPlanData(
                plan.getId(),
                plan.getPlanType(),
                plan.getSlotLengthMinutes(),
                plan.getSlotPrice(),
                plan.getUpfrontAmount(),
                plan.getAllowedOvertimeMinutes(),
                plan.getOvertimeSurchargeRatio(),
                plan.getFreeRadiusKm(),
                plan.getOutOfRadiusRate(),
                plan.getWaitingFreeMinutes(),
                plan.getWaitingRate(),
                plan.getWeekendSurchargeRatio(),
                plan.getHourlyToDailyThresholdMinutes());
    }
}
