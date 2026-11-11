package com.fptufa26.ecomotion.review.dto.response;

import java.time.Instant;
import java.util.List;

public record PublicReviewsResponse(
        String target,
        Long targetId,
        String targetName,
        String targetImageUrl,
        double averageRating,
        long reviewCount,
        List<Long> distribution,
        List<Item> items
) {
    public record Item(Long id, Integer rating, String comment, List<String> mediaUrls, String reviewerName, Instant createdAt) {
    }
}
