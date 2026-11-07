package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.*;
import com.fptufa26.ecomotion.booking.service.*;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.enums.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class YearlyReviewController {
    private final YearlyWorkflowService yearly;
    private final TripEvidenceService evidence;
    @PostMapping("/api/renter/yearly-bookings/{code}/reviews")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.View> complain(@AuthenticationPrincipal SecurityUser user, @PathVariable String code,
                                                           @Valid @RequestBody Complaint request) {
        return ApiResponse.success(yearly.disputeSettlement(user.getUserId(), code, request));
    }
    @PostMapping("/api/provider/yearly-bookings/{code}/reviews/reply")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> reply(@AuthenticationPrincipal SecurityUser user, @PathVariable String code,
                                                       @Valid @RequestBody Reply request) {
        return ApiResponse.success(yearly.replyReview(user.getUserId(), code, request));
    }
    @GetMapping({"/api/renter/yearly-bookings/{code}/reviews/history", "/api/provider/yearly-bookings/{code}/reviews/history"})
    @PreAuthorize("hasAnyRole('RENTER','PROVIDER')")
    public ApiResponse<?> history(@AuthenticationPrincipal SecurityUser user, @PathVariable String code) {
        return ApiResponse.success(java.util.Map.of("history", yearly.reviewHistory(user.getUserId(), code),
                "reviews", yearly.reviewCases(user.getUserId(), code)));
    }
    @GetMapping("/api/admin/yearly-bookings/{code}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<?> review(@AuthenticationPrincipal SecurityUser user, @PathVariable String code) {
        return ApiResponse.success(java.util.Map.of("workflow", yearly.adminView(user.getUserId(), code),
                "history", yearly.reviewHistory(user.getUserId(), code), "reviews", yearly.reviewCases(user.getUserId(), code),
                "evidence", evidence.listEvidence(user.getUserId(), Role.ADMIN, code)));
    }
    @PostMapping("/api/admin/yearly-bookings/{code}/reviews/settlement")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<YearlyWorkflowService.View> settlement(@AuthenticationPrincipal SecurityUser user, @PathVariable String code,
                                                            @Valid @RequestBody SettlementRuling request) {
        return ApiResponse.success(yearly.adjudicateSettlement(user.getUserId(), code, request));
    }
    @PostMapping("/api/admin/yearly-bookings/{code}/reviews/cancellation")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> cancellation(@AuthenticationPrincipal SecurityUser user, @PathVariable String code,
                                         @Valid @RequestBody CancellationRuling request) {
        yearly.adjudicateCancellation(user.getUserId(), code, request);
        return ApiResponse.success(null);
    }
}
