package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingSummaryResponse(
        String bookingCode,
        BookingStatus status,
        PlanType planType,
        String vehicleName,
        int manufactureYear,
        String licensePlate,
        String imageUrl,
        String pickupAddress,
        Instant pickupTime,
        int passengerCount,
        BigDecimal totalAmount
) {
}
