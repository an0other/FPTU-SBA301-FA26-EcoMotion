package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.PlanType;

import java.math.BigDecimal;
import java.time.Instant;

public record CheckoutPreviewResponse(
        String bookingCode,
        String vehicleName,
        int manufactureYear,
        String licensePlate,
        String imageUrl,
        String pickupAddress,
        Instant pickupTime,
        Instant endTime,
        int passengerCount,
        int slotCount,
        PlanType planType,
        BigDecimal totalAmount,
        BigDecimal depositRatio,
        BigDecimal amountDue,
        boolean upfront
) {
}
