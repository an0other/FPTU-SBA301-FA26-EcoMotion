package com.fptufa26.ecomotion.review.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.util.List;

public record BookingReviewsResponse(
        String bookingCode,
        BookingStatus bookingStatus,
        String vehicleName,
        boolean hasDriver,
        String driverName,
        boolean canReview,
        String restrictionReason,
        List<ReviewResponse> reviews
) {
}