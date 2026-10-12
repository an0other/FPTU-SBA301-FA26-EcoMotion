package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProviderBookingResponse(
        String bookingCode,
        String vehicleName,
        String vehicleImageUrl,
        String licensePlate,
        String renterName,
        Instant pickupTime,
        String pickupAddress,
        BookingStatus status,
        BigDecimal totalAmount
) {
}
