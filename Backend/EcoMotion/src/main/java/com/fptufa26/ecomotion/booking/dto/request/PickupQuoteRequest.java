package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PickupQuoteRequest(
        @NotNull Long vehicleId,
        @NotNull Long planId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal pickupLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal pickupLongitude
) {
}
