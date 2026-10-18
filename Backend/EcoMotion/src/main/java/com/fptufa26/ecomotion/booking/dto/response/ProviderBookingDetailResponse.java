package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProviderBookingDetailResponse(
        Long id,
        String bookingCode,
        BookingStatus status,
        Instant createdAt,
        Instant updatedAt,

        // Renter details
        RenterInfo renter,

        // Vehicle details
        VehicleInfo vehicle,

        // Plan details
        PlanInfo rentalPlan,

        // Trip details
        TripInfo trip,

        // Assigned Driver (nullable)
        DriverInfo driver,

        // Financial breakdown
        FinancialInfo pricing,

        // Policy Snapshot (nullable)
        PolicySnapshotInfo policySnapshot,

        // Cancellation (nullable)
        CancellationInfo cancellation,

        // Activity timeline
        List<TimelineEvent> timeline
) {
    public record RenterInfo(
            Long id,
            Long userId,
            String fullName,
            String phone,
            String email,
            String avatarUrl,
            boolean licenseVerified
    ) {}

    public record VehicleInfo(
            Long id,
            String brand,
            String model,
            String licensePlate,
            Integer seatCount,
            Transmission transmission,
            FuelType fuelType,
            String color,
            Integer manufactureYear,
            List<String> imageUrls
    ) {}

    public record PlanInfo(
            Long id,
            PlanType planType,
            Integer slotLengthMinutes,
            BigDecimal slotPrice,
            BigDecimal upfrontAmount
    ) {}

    public record TripInfo(
            String pickupAddress,
            Instant pickupTime,
            Instant estimatedEndTime,
            Instant actualStartAt,
            Instant actualEndAt,
            Integer passengerCount,
            String contractUrl,
            Instant renterConfirmedAt,
            Instant providerConfirmedAt
    ) {}

    public record DriverInfo(
            Long id,
            Long userId,
            String fullName,
            String phone,
            String licenseNo,
            String licenseClass,
            String portraitUrl,
            BigDecimal ratingAvg,
            Integer ratingCount
    ) {}

    public record FinancialInfo(
            BigDecimal baseRentalAmount,
            BigDecimal totalAmount,
            List<BookingChargeResponse> charges,
            List<PaymentTransactionResponse> transactions
    ) {}

    public record PolicySnapshotInfo(
            Long id,
            PlanType planType,
            BigDecimal slotPrice,
            Integer slotLengthMinutes,
            BigDecimal depositRatio,
            BigDecimal upfrontAmount,
            Integer allowedOvertimeMinutes,
            BigDecimal overtimeSurchargeRatio,
            Integer waitingFreeMinutes,
            BigDecimal waitingRate,
            BigDecimal weekendSurchargeRatio,
            Integer freeRadiusKm,
            BigDecimal outOfRadiusRate
    ) {}

    public record CancellationInfo(
            Long cancelledById,
            String cancelledByName,
            CancelReason cancelReason,
            Instant cancelledAt
    ) {}

    public record TimelineEvent(
            String title,
            String description,
            Instant timestamp,
            String actor
    ) {}
}
