package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;
import com.fptufa26.ecomotion.finance.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping({"/api/v1/refunds", "/api/refunds"})
@RequiredArgsConstructor
@Tag(name = "Refund Management", description = "Refund requests, provider/admin approval workflow, and reconciliation")
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit a new refund request for a booking")
    public ApiResponse<RefundResponse> createRefund(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody CreateRefundRequest request) {
        return ApiResponse.success(refundService.createRefundRequest(principal.getUserId(), request));
    }

    @PostMapping("/{refundId}/review")
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "Provider or Admin approves or rejects a refund request")
    public ApiResponse<RefundResponse> reviewRefund(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long refundId,
            @Valid @RequestBody ReviewRefundRequest request) {
        return ApiResponse.success(refundService.reviewRefundRequest(principal.getUserId(), refundId, request));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all refund requests for a specific booking")
    public ApiResponse<List<RefundResponse>> listRefundsByBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId) {
        return ApiResponse.success(refundService.listRefundsByBooking(principal.getUserId(), bookingId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "List all refund requests for the authenticated provider")
    public ApiResponse<List<RefundResponse>> listRefundsForProvider(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(refundService.listRefundsForProvider(principal.getUserId()));
    }
}
