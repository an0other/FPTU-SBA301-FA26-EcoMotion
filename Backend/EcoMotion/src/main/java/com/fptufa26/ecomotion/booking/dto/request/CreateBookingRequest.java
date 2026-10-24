package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateBookingRequest(
        @NotNull Long vehicleId,
        @NotNull Long planId,
        @NotNull @Min(1) Integer slotCount,
        @NotNull Instant pickupTime,
        @NotBlank @Size(max = 500) String pickupAddress,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal pickupLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal pickupLongitude,
        String driverNote,
        @NotNull @Min(1) Integer passengerCount
) {
}
