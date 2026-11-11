package com.fptufa26.ecomotion.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(
        @NotNull
        @Min(1)
        @Max(5)
        Integer rating,

        @Size(max = 1000)
        String comment,
        @Size(max = 5)
        java.util.List<String> mediaUrls
) {
    public UpdateReviewRequest(Integer rating, String comment) {
        this(rating, comment, null);
    }

}