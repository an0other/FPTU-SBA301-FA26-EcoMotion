package com.fptufa26.ecomotion.review.service;

import com.fptufa26.ecomotion.review.dto.request.CreateReviewRequest;
import com.fptufa26.ecomotion.review.dto.response.ReviewResponse;

import java.util.List;

public interface ReviewService {

    List<ReviewResponse> getBookingReviews(
            Long userId,
            String bookingCode);

    ReviewResponse createReview(
            Long userId,
            String bookingCode,
            CreateReviewRequest request);
}