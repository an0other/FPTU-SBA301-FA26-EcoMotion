package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingDetailResponse(
        String bookingCode,
        BookingStatus status,
        PlanType planType,
        Long vehicleId,
        String vehicleName,
        int manufactureYear,
        String licensePlate,
        String imageUrl,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String driverNote,
        Instant pickupTime,
        Instant endTime,
        int passengerCount,
        int slotCount,
        BigDecimal totalAmount,
        BigDecimal depositAmount,
        Instant createdAt,
        Instant actualStartAt,
        Instant actualEndAt,
        Instant renterConfirmedAt,
        Instant providerConfirmedAt,
        CancelReason cancelReason,
        Instant cancelledAt,
        DriverSnippetResponse driver,
        List<BookingChargeLineResponse> charges,
        List<TimelineStepResponse> timeline,
        RefundPreviewResponse refundPreview,
        boolean canCancel,
        boolean canEditRequest,
        boolean canPay,
        boolean canConfirmStart,
        boolean canConfirmEnd,
        List<DisputeCategory> disputeCategories,
        String providerName,
        Instant holdExpiresAt,
        List<RentalQuoteResponse.Item> pricingItems
) {
}
