package com.fptufa26.ecomotion.vehicle.service.impl;

import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.vehicle.dto.response.BookableVehicleResponse;
import com.fptufa26.ecomotion.vehicle.entity.PlanStatus;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.entity.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.mapper.VehicleCatalogMapper;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleCatalogServiceImpl implements VehicleCatalogService {

    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final VehicleCatalogMapper vehicleCatalogMapper;

    @Override
    public List<BookableVehicleResponse> listApproved() {
        return vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.APPROVED).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Vehicle requireApproved(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));
        if (vehicle.getStatus() != VehicleStatus.APPROVED) {
            throw new NotFoundException("Vehicle not found");
        }
        return vehicle;
    }

    @Override
    public RentalPlan requireActivePlan(Long vehicleId, Long planId) {
        return rentalPlanRepository.findByIdAndVehicle_Id(planId, vehicleId)
                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Rental plan not found"));
    }

    @Override
    public String coverImageUrl(Long vehicleId) {
        List<VehicleImage> images = vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(vehicleId);
        return images.isEmpty() ? null : images.getFirst().getImageUrl();
    }

    private BookableVehicleResponse toResponse(Vehicle vehicle) {
        List<VehicleImage> images = vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(vehicle.getId());
        String imageUrl = images.isEmpty() ? null : images.getFirst().getImageUrl();
        List<RentalPlan> plans = rentalPlanRepository
                .findByVehicle_IdAndStatusOrderByIdAsc(vehicle.getId(), PlanStatus.ACTIVE);
        return vehicleCatalogMapper.toBookableVehicle(vehicle, imageUrl, plans);
    }
}
