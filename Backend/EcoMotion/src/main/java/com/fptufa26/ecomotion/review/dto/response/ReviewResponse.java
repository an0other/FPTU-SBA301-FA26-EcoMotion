package com.fptufa26.ecomotion.review.dto.response;

import com.fptufa26.ecomotion.review.enums.ReviewTarget;

import java.time.Instant;

public record ReviewResponse(
        String bookingCode,
        ReviewTarget target,
        Integer rating,
        String comment,
        String media,
        Instant createdAt
) {
}