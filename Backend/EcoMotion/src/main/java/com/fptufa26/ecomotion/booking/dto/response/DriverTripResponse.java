package com.fptufa26.ecomotion.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DriverTripResponse(
        Long tripId,
        Long bookingId,
        String bookingCode,
        BookingStatus status,
        BookingStatus bookingStatus,
        String tripStatus,
        Instant scheduledPickupAt,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String driverNote,
        Integer passengerCount,
        VehicleSummary vehicle,
        CustomerSummary customer,
        Instant actualStartAt,
        Instant actualEndAt,
        String rentalType,
        String rentalDuration,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant assignedAt,
        Instant driverConfirmedAt,
        Instant actualArrivedAt,
        String executionStatus,
        Instant assignmentExpiresAt
) {
    public DriverTripResponse(
            Long tripId,
            String bookingCode,
            BookingStatus status,
            Instant scheduledPickupAt,
            String pickupAddress,
            Integer passengerCount,
            VehicleSummary vehicle,
            CustomerSummary customer,
            Instant actualStartAt,
            Instant actualEndAt,
            String rentalType,
            String rentalDuration,
            BigDecimal totalAmount,
            Instant createdAt
    ) {
        this(
                tripId,
                tripId,
                bookingCode,
                status,
                status,
                status != null ? status.name() : null,
                scheduledPickupAt,
                pickupAddress,
                null,
                null,
                null,
                passengerCount,
                vehicle,
                customer,
                actualStartAt,
                actualEndAt,
                rentalType,
                rentalDuration,
                totalAmount,
                createdAt,
                null,
                null,
                null,
                status != null ? status.name() : null,
                null
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VehicleSummary(
            Long id,
            String name,
            String brand,
            String model,
            String licensePlate,
            String color,
            Integer seatCount,
            String coverUrl
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerSummary(
            String fullName,
            String phone
    ) {}
}
