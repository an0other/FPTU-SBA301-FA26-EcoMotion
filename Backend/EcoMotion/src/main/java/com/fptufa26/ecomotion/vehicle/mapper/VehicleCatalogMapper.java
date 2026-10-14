package com.fptufa26.ecomotion.vehicle.mapper;

import com.fptufa26.ecomotion.vehicle.dto.response.BookableVehicleResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.RentalPlanResponse;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface VehicleCatalogMapper {

    @Mapping(target = "name", expression = "java(vehicle.getBrand() + \" \" + vehicle.getModel())")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "depositRatio", source = "vehicle.provider.depositRatio")
    @Mapping(target = "plans", source = "plans")
    BookableVehicleResponse toBookableVehicle(Vehicle vehicle, String imageUrl, List<RentalPlan> plans);

    RentalPlanResponse toPlanResponse(RentalPlan plan);
}
