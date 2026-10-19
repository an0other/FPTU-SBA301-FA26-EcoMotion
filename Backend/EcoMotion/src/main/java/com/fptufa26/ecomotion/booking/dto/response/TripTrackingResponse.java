package com.fptufa26.ecomotion.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripTrackingResponse(
        Long tripId,
        String bookingCode,
        BookingStatus status,
        String executionStatus,
        Instant assignedAt,
        Instant driverConfirmedAt,
        Instant actualArrivedAt,
        Instant actualStartAt,
        Instant actualEndAt,
        Instant scheduledPickupAt,
        String pickupAddress,
        Double pickupLatitude,
        Double pickupLongitude,
        TripLocationResponse latestLocation,
        DriverSummary driver,
        VehicleSummary vehicle
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DriverSummary(
            Long id,
            String fullName,
            String phone,
            String licenseNo,
            String portraitUrl
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VehicleSummary(
            Long id,
            String name,
            String brand,
            String model,
            String licensePlate
    ) {}
}
