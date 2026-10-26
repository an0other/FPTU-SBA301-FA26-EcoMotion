package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundProofResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;
import com.fptufa26.ecomotion.finance.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Refund Management", description = "Refund request workflow plus VietQR refund proof on the payment ledger")
public class RefundController {

    private final RefundService refundService;

    @PostMapping({"/api/v1/refunds", "/api/refunds"})
    @Operation(summary = "Submit a new refund request for a booking")
    public ApiResponse<RefundResponse> createRefund(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody CreateRefundRequest request) {
        return ApiResponse.success(refundService.createRefundRequest(principal.getUserId(), request));
    }

    @PostMapping({"/api/v1/refunds/{refundId}/review", "/api/refunds/{refundId}/review"})
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "Provider or Admin approves or rejects a refund request")
    public ApiResponse<RefundResponse> reviewRefund(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long refundId,
            @Valid @RequestBody ReviewRefundRequest request) {
        return ApiResponse.success(refundService.reviewRefundRequest(principal.getUserId(), refundId, request));
    }

    @GetMapping({"/api/v1/refunds/booking/{bookingId}", "/api/refunds/booking/{bookingId}"})
    @Operation(summary = "List all refund requests for a specific booking")
    public ApiResponse<List<RefundResponse>> listRefundsByBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId) {
        return ApiResponse.success(refundService.listRefundsByBooking(principal.getUserId(), bookingId));
    }

    @GetMapping({"/api/v1/refunds", "/api/refunds"})
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "List all refund requests for the authenticated provider")
    public ApiResponse<List<RefundResponse>> listRefundsForProvider(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(refundService.listRefundsForProvider(principal.getUserId()));
    }

    @PostMapping(value = {"/api/v1/payment-transactions/{id}/refund-proof",
            "/api/payment-transactions/{id}/refund-proof"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "Attach bank-transfer proof and mark a pending refund ledger row as paid")
    public ApiResponse<RefundProofResponse> completeRefund(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam String bankReference,
            @RequestParam(required = false) String note) {
        return ApiResponse.success(refundService.completeRefund(principal.getUserId(), id, file, bankReference, note));
    }

    @GetMapping({"/api/v1/payment-transactions/{id}/refund-proof",
            "/api/payment-transactions/{id}/refund-proof"})
    @Operation(summary = "View an authorized refund-proof file")
    public ResponseEntity<byte[]> viewRefundProof(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id) {
        PaymentEvidenceFileResponse file = refundService.getProof(principal.getUserId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
