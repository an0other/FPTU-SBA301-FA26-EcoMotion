package com.fptufa26.ecomotion.review.dto.response;

import java.time.Instant;
import java.util.List;

/** Đánh giá công khai của một xe hoặc một tài xế: điểm trung bình, phân bố sao và danh sách nhận xét. */
public record PublicReviewsResponse(
        String target,
        Long targetId,
        String targetName,
        String targetImageUrl,
        double averageRating,
        long reviewCount,
        /** Số lượt theo sao, chỉ số 0 = 1 sao ... 4 = 5 sao. */
        List<Long> distribution,
        List<Item> items
) {
    public record Item(Long id, Integer rating, String comment, List<String> mediaUrls, String reviewerName, Instant createdAt) {
    }
}
