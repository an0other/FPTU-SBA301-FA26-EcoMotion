package com.fptufa26.ecomotion.review.service;

import com.fptufa26.ecomotion.review.dto.request.CreateReviewRequest;
import com.fptufa26.ecomotion.review.dto.response.ReviewResponse;
import com.fptufa26.ecomotion.review.dto.request.UpdateReviewRequest;
import com.fptufa26.ecomotion.review.enums.ReviewTarget;
import com.fptufa26.ecomotion.review.dto.response.BookingReviewsResponse;

public interface ReviewService {

    String uploadReviewImage(Long userId, String bookingCode, org.springframework.web.multipart.MultipartFile file);

//    List<ReviewResponse> getBookingReviews(Long userId, String bookingCode);
BookingReviewsResponse getBookingReviews(
        Long userId,
        String bookingCode);

    ReviewResponse createReview(Long userId, String bookingCode, CreateReviewRequest request);

    ReviewResponse updateReview(Long userId, String bookingCode, ReviewTarget target, UpdateReviewRequest request);

    void deleteReview(Long userId, String bookingCode, ReviewTarget target);
}