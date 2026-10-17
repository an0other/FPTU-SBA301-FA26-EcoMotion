package com.fptufa26.ecomotion.vehicle.dto.request;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(
        @NotBlank @Size(max = 50) String chassisNo,
        @NotBlank @Size(max = 50) String engineNo,
        @NotBlank @Size(max = 20) String licensePlate,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        @NotNull @Min(2015) @Max(2026) Integer manufactureYear,
        @NotNull @Min(1) Integer seatCount,
        @NotNull Transmission transmission,
        @NotNull FuelType fuelType,
        @NotBlank @Size(max = 40) String color,
        @NotNull @Min(0) Integer maintenanceBufferMinutes,
        @Size(max = 500) String coverImageUrl
) {
}
