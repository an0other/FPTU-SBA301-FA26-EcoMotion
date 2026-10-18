package com.fptufa26.ecomotion.review.dto.request;

import com.fptufa26.ecomotion.review.enums.ReviewTarget;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull
        ReviewTarget target,

        @NotNull
        @Min(1)
        @Max(5)
        Integer rating,

        @Size(max = 1000)
        String comment
) {
    public static class UpdateReviewRequest {
    }
}