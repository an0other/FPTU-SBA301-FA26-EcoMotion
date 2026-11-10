package com.fptufa26.ecomotion.review.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.review.dto.response.PublicReviewsResponse;
import com.fptufa26.ecomotion.review.service.PublicReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Public Reviews")
public class PublicReviewController {

    private final PublicReviewService publicReviews;

    /** Công khai cùng nhóm GET /api/vehicles/** — khách chưa đăng nhập vẫn xem được khi chọn xe. */
    @GetMapping("/api/vehicles/{vehicleId}/reviews")
    @Operation(summary = "Public reviews of a vehicle")
    public ApiResponse<PublicReviewsResponse> vehicleReviews(@PathVariable Long vehicleId) {
        return ApiResponse.success(publicReviews.forVehicle(vehicleId));
    }

    /** Cần đăng nhập (mọi vai trò). */
    @GetMapping("/api/reviews/drivers/{driverId}")
    @Operation(summary = "Reviews of a driver")
    public ApiResponse<PublicReviewsResponse> driverReviews(@PathVariable Long driverId) {
        return ApiResponse.success(publicReviews.forDriver(driverId));
    }
}
