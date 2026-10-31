package com.fptufa26.ecomotion.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DriverTripDetailResponse(
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
        String contractUrl,
        Instant actualStartAt,
        Instant actualEndAt,
        Instant renterConfirmedAt,
        Instant providerConfirmedAt,
        Instant cancelledAt,
        CancelReason cancelReason,
        VehicleDetail vehicle,
        CustomerSummary customer,
        ProviderSummary provider,
        String rentalType,
        String rentalDuration,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        Instant assignedAt,
        Instant driverConfirmedAt,
        Instant actualArrivedAt,
        String executionStatus,
        TripLocationResponse latestLocation,
        Instant plannedEndAt,
        List<RentalQuoteResponse.Item> pricingItems
) {
    public DriverTripDetailResponse(
            Long tripId,
            String bookingCode,
            BookingStatus status,
            Instant scheduledPickupAt,
            String pickupAddress,
            Integer passengerCount,
            String contractUrl,
            Instant actualStartAt,
            Instant actualEndAt,
            Instant renterConfirmedAt,
            Instant providerConfirmedAt,
            Instant cancelledAt,
            CancelReason cancelReason,
            VehicleDetail vehicle,
            CustomerSummary customer,
            ProviderSummary provider,
            String rentalType,
            String rentalDuration,
            BigDecimal totalAmount,
            Instant createdAt,
            Instant updatedAt
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
                contractUrl,
                actualStartAt,
                actualEndAt,
                renterConfirmedAt,
                providerConfirmedAt,
                cancelledAt,
                cancelReason,
                vehicle,
                customer,
                provider,
                rentalType,
                rentalDuration,
                totalAmount,
                createdAt,
                updatedAt,
                null,
                null,
                null,
                status != null ? status.name() : null,
                null,
                null,
                List.of()
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VehicleDetail(
            Long id,
            String name,
            String brand,
            String model,
            String licensePlate,
            Integer seatCount,
            String transmission,
            String fuelType,
            String color,
            String coverUrl
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomerSummary(
            String fullName,
            String phone,
            String avatarUrl
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProviderSummary(
            Long id,
            String displayName,
            String hotline,
            String depotAddress
    ) {}
}
