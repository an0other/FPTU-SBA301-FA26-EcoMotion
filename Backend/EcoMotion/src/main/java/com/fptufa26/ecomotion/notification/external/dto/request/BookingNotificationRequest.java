package com.fptufa26.ecomotion.notification.external.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingNotificationRequest(
        String email,
        String phone,
        @NotBlank String bookingCode,
        @NotBlank String vehicleName,
        @NotBlank String pickupAddress,
        @NotNull Instant pickupTime,
        @NotNull @PositiveOrZero BigDecimal depositAmount
) {
}
