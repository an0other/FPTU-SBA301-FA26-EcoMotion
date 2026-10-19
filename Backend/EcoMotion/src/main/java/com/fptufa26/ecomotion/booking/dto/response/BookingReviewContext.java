package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

public record BookingReviewContext(
        Long bookingId,
        String bookingCode,
        BookingStatus status,
        Long vehicleId,
        Long vehicleProviderId,
        Long driverId,
        Long driverProviderId,
        String vehicleName,
        String driverName
) {
}