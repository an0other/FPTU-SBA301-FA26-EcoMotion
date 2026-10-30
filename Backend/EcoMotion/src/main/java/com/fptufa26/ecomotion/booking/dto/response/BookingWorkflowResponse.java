package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.OperationalResolutionType;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;

public record BookingWorkflowResponse(
        String bookingCode, BookingStatus bookingStatus, String paymentStatus,
        String driverStatus, String tripStatus, String settlementStatus,
        BigDecimal confirmedPaid, BigDecimal refundedAmount, BigDecimal pendingRefundAmount,
        BigDecimal refundableAmount, BigDecimal remainingAmount, Instant resolutionDeadline,
        Instant assignmentExpiresAt, boolean needsDriver, boolean canProposeAlternative,
        String nextAction, Proposal proposal, List<DriverFeedback> driverFeedback,
        List<RefundResponse> refunds, Instant updatedAt) {
    public record Proposal(String id, OperationalResolutionType resolution, Long vehicleId,
            String vehicleName, Instant pickupTime, String reason, String status,
            Instant createdAt, Instant respondedAt, String responseReason) { }
    public record DriverFeedback(String driverName, String status, Instant respondedAt,
            String reason) { }
}
