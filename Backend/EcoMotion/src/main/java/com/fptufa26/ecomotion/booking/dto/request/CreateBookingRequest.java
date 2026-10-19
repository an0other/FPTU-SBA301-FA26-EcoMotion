package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateBookingRequest(
        @NotNull Long vehicleId,
        @NotNull Long planId,
        @NotNull @Min(1) Integer slotCount,
        @NotNull Instant pickupTime,
        @NotBlank @Size(max = 500) String pickupAddress,
        @NotNull @Min(1) Integer passengerCount
) {
}
