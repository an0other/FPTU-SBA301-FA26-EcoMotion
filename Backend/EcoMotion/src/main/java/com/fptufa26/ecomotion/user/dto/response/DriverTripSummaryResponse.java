package com.fptufa26.ecomotion.user.dto.response;

import java.time.Instant;

public record DriverTripSummaryResponse(
        String bookingCode,
        String vehicleName,
        String pickupAddress,
        Instant pickupTime,
        String status,
        // Hạn phản hồi của yêu cầu DRIVER_ASSIGNED (null với các trạng thái khác). Dùng cho đếm ngược trên UI.
        Instant assignmentExpiresAt
) {
}
