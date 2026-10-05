package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import com.fptufa26.ecomotion.booking.entity.CancelReason;
import com.fptufa26.ecomotion.dispute.entity.DisputeCategory;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingDetailResponse(
        String bookingCode,
        BookingStatus status,
        PlanType planType,
        String vehicleName,
        int manufactureYear,
        String licensePlate,
        String imageUrl,
        String pickupAddress,
        Instant pickupTime,
        Instant endTime,
        int passengerCount,
        int slotCount,
        BigDecimal totalAmount,
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
        boolean canConfirmEnd,
        List<DisputeCategory> disputeCategories
) {
}
