package com.fptufa26.ecomotion.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderBookingDetailResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        BookingStatus status,
        BookingStatus bookingStatus,
        Instant pickupTime,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String driverNote,
        Integer passengerCount,
        String contractUrl,
        BigDecimal totalAmount,
        Instant actualStartAt,
        Instant actualEndAt,
        Instant renterConfirmedAt,
        Instant providerConfirmedAt,
        Instant cancelledAt,
        CancelReason cancelReason,
        String cancelledByName,
        Instant createdAt,
        Instant updatedAt,

        Long renterId,
        String renterName,
        String renterPhone,
        String renterEmail,
        String renterAvatarUrl,

        Long vehicleId,
        String vehicleName,
        String licensePlate,
        String brand,
        String model,
        Integer modelYear,
        Integer seats,
        Transmission transmission,
        FuelType fuelType,
        String color,
        String vehicleImageUrl,
        List<String> vehicleImageUrls,

        String rentalType,
        String rentalDuration,
        BigDecimal slotPrice,
        Integer slotLengthMinutes,
        PlanType planType,
        BigDecimal depositRatio,
        BigDecimal upfrontAmount,
        Integer allowedOvertimeMinutes,
        BigDecimal overtimeSurchargeRatio,
        Integer waitingFreeMinutes,
        BigDecimal waitingRate,
        BigDecimal weekendSurchargeRatio,
        Integer freeRadiusKm,
        BigDecimal outOfRadiusRate,

        Long driverId,
        String driverName,
        String driverPhone,
        String driverLicenseNo,
        String driverLicenseClass,
        String driverPortraitUrl,
        BigDecimal driverRatingAvg,

        DriverSummary driver,
        TripSummary trip,

        List<BookingChargeResponse> charges,
        List<PaymentTransactionDetailResponse> paymentTransactions,
        List<TimelineEventResponse> timeline,

        Instant assignedAt,
        Instant driverConfirmedAt,
        Instant actualArrivedAt,
        String executionStatus,
        TripLocationResponse latestLocation,
        Instant plannedEndAt,
        List<RentalQuoteResponse.Item> pricingItems
) {
    public record DriverSummary(
            Long id,
            String name,
            String phone,
            String licenseNo,
            String licenseClass,
            String portraitUrl,
            BigDecimal ratingAvg
    ) {}

    public record TripSummary(
            Long id,
            String status,
            Instant actualArrivalAt,
            Instant actualStartAt,
            Instant actualEndAt
    ) {}

    public record PaymentTransactionDetailResponse(
            Long id,
            String type,
            BigDecimal amount,
            String direction,
            String method,
            String status,
            String receiptUrl,
            Instant paidAt,
            Instant createdAt
    ) {}

    public record TimelineEventResponse(
            String title,
            String description,
            Instant timestamp,
            String status,
            String icon
    ) {}
}
