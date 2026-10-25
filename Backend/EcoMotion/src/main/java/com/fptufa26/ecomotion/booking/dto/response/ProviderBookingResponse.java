package com.fptufa26.ecomotion.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderBookingResponse(
        Long bookingId,
        String bookingCode,
        String vehicleName,
        String vehicleImageUrl,
        String licensePlate,
        String renterName,
        Integer passengerCount,
        String rentalType,
        String rentalDuration,
        Instant pickupTime,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String driverNote,
        Long driverId,
        String driverName,
        BookingStatus status,
        String executionStatus,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        Instant assignedAt,
        Instant driverConfirmedAt,
        Instant actualArrivedAt,
        Instant actualStartAt,
        Instant actualEndAt
) {
    public ProviderBookingResponse(
            Long bookingId,
            String bookingCode,
            String vehicleName,
            String vehicleImageUrl,
            String licensePlate,
            String renterName,
            Integer passengerCount,
            String rentalType,
            String rentalDuration,
            Instant pickupTime,
            String pickupAddress,
            BigDecimal pickupLatitude,
            BigDecimal pickupLongitude,
            String driverNote,
            String driverName,
            BookingStatus status,
            String executionStatus,
            BigDecimal totalAmount,
            Instant createdAt,
            Instant updatedAt,
            Instant assignedAt,
            Instant driverConfirmedAt,
            Instant actualArrivedAt,
            Instant actualStartAt,
            Instant actualEndAt
    ) {
        this(
                bookingId,
                bookingCode,
                vehicleName,
                vehicleImageUrl,
                licensePlate,
                renterName,
                passengerCount,
                rentalType,
                rentalDuration,
                pickupTime,
                pickupAddress,
                pickupLatitude,
                pickupLongitude,
                driverNote,
                null,
                driverName,
                status,
                executionStatus,
                totalAmount,
                createdAt,
                updatedAt,
                assignedAt,
                driverConfirmedAt,
                actualArrivedAt,
                actualStartAt,
                actualEndAt
        );
    }

    public ProviderBookingResponse(
            String bookingCode,
            String vehicleName,
            String vehicleImageUrl,
            String licensePlate,
            String renterName,
            Integer passengerCount,
            String rentalType,
            String rentalDuration,
            Instant pickupTime,
            String pickupAddress,
            String driverName,
            BookingStatus status,
            BigDecimal totalAmount,
            Instant createdAt
    ) {
        this(
                null,
                bookingCode,
                vehicleName,
                vehicleImageUrl,
                licensePlate,
                renterName,
                passengerCount,
                rentalType,
                rentalDuration,
                pickupTime,
                pickupAddress,
                null,
                null,
                null,
                null,
                driverName,
                status,
                status != null ? status.name() : null,
                totalAmount,
                createdAt,
                createdAt,
                null,
                null,
                null,
                null,
                null
        );
    }
}
