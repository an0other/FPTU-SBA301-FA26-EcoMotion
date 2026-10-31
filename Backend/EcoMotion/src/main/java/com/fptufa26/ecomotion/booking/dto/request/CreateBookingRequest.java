package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.Valid;
import java.util.List;

public record CreateBookingRequest(
        @NotNull Long vehicleId,
        Long planId,
        @Min(1) Integer slotCount,
        @NotNull Instant pickupTime,
        @NotBlank @Size(max = 500) String pickupAddress,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal pickupLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal pickupLongitude,
        String driverNote,
        @NotNull @Min(1) Integer passengerCount,
        Instant plannedEndAt,
        @Valid List<PricingItemRequest> pricingItems,
        BigDecimal expectedRentalAmount
) {
    public CreateBookingRequest(Long vehicleId, Long planId, Integer slotCount, Instant pickupTime,
            String pickupAddress, BigDecimal pickupLatitude, BigDecimal pickupLongitude,
            String driverNote, Integer passengerCount) {
        this(vehicleId, planId, slotCount, pickupTime, pickupAddress, pickupLatitude, pickupLongitude,
                driverNote, passengerCount, null, null, null);
    }
}
