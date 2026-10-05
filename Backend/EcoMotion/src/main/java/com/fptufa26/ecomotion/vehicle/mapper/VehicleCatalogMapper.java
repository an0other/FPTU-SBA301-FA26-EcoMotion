package com.fptufa26.ecomotion.vehicle.mapper;

import com.fptufa26.ecomotion.vehicle.dto.response.BookableVehicleResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.RentalPlanResponse;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VehicleCatalogMapper {

    public BookableVehicleResponse toBookableVehicle(Vehicle vehicle, String imageUrl, List<RentalPlan> plans) {
        return new BookableVehicleResponse(
                vehicle.getId(),
                vehicle.getBrand() + " " + vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getLicensePlate(),
                vehicle.getSeatCount(),
                imageUrl,
                vehicle.getProvider().getDepositRatio(),
                plans.stream().map(this::toPlanResponse).toList());
    }

    public RentalPlanResponse toPlanResponse(RentalPlan plan) {
        return new RentalPlanResponse(
                plan.getId(),
                plan.getPlanType(),
                plan.getSlotLengthMinutes(),
                plan.getSlotPrice(),
                plan.getUpfrontAmount());
    }
}
