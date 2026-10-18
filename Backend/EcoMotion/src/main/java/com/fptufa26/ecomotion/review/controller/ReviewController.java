package com.fptufa26.ecomotion.review.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.review.dto.request.CreateReviewRequest;
import com.fptufa26.ecomotion.review.dto.response.ReviewResponse;
import com.fptufa26.ecomotion.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/renter/bookings/{bookingCode}/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RENTER')")
@Tag(name = "Renter Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "Get the authenticated renter's reviews for a booking")
    public ApiResponse<List<ReviewResponse>> getBookingReviews(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode) {
        return ApiResponse.success(
                reviewService.getBookingReviews(
                        principal.getUserId(),
                        bookingCode));
    }

    @PostMapping
    @Operation(summary = "Review a vehicle or driver after booking completion")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse response = reviewService.createReview(
                principal.getUserId(),
                bookingCode,
                request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}