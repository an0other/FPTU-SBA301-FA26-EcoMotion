package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportIssueRequest;
import com.fptufa26.ecomotion.finance.dto.request.SimulatePaymentRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderTransactionResponse;
import com.fptufa26.ecomotion.finance.dto.response.SimulatePaymentResponse;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Direct Payment & VietQR", description = "Direct bank transfer payment requests, customer proof uploads, provider confirmation, and sandbox simulation")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;
    private final BookingService bookingService;

    // ==========================================
    // 1. Payment Requests (Renter, Provider, Admin)
    // ==========================================

    @GetMapping({"/api/v1/bookings/{bookingId}/payment-requests", "/api/bookings/{bookingId}/payment-requests"})
    @Operation(summary = "Get list of payment requests (DEPOSIT, FINAL_BALANCE) for a booking")
    public ApiResponse<List<PaymentRequestResponse>> getPaymentRequestsByBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long bookingId) {
        return ApiResponse.success(paymentRequestService.getPaymentRequestsByBooking(bookingId, principal.getUserId()));
    }

    @GetMapping({"/api/v1/renter/bookings/{bookingCode}/payment-requests", "/api/renter/bookings/{bookingCode}/payment-requests"})
    @Operation(summary = "Get VietQR requests for a renter booking code")
    public ApiResponse<List<PaymentRequestResponse>> getPaymentRequestsByBookingCode(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode) {
        Booking booking = bookingService.findByBookingCode(bookingCode)
                .or(() -> {
                    try {
                        return bookingService.getBookingById(Long.parseLong(bookingCode));
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingCode));
        return ApiResponse.success(paymentRequestService.getPaymentRequestsByBooking(
                booking.getId(), principal.getUserId()));
    }

    @GetMapping({"/api/v1/payment-requests/{id}", "/api/payment-requests/{id}"})
    @Operation(summary = "Get payment request details and VietQR instructions")
    public ApiResponse<PaymentRequestResponse> getPaymentRequestDetail(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id) {
        return ApiResponse.success(paymentRequestService.getPaymentRequestDetail(id, principal.getUserId()));
    }

    @PostMapping({"/api/v1/payment-requests/{id}/proofs", "/api/payment-requests/{id}/proofs"})
    @Operation(summary = "Deprecated proof URL submission (disabled; use multipart upload)")
    public ResponseEntity<Void> rejectLegacyProofUrlSubmission(@PathVariable Long id) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.GONE).build();
    }

    @PostMapping(value = {"/api/v1/payment-requests/{id}/proofs/multipart",
            "/api/payment-requests/{id}/proofs/multipart"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Customer uploads an image proof of bank transfer")
    public ApiResponse<PaymentEvidenceResponse> uploadPaymentProof(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam BigDecimal transferredAmount,
            @RequestParam(required = false) String transactionReference,
            @RequestParam(required = false) Instant transferredAt,
            @RequestParam(required = false) String note) {
        return ApiResponse.success(paymentRequestService.submitPaymentProof(
                id, principal.getUserId(), file, transferredAmount,
                transactionReference, transferredAt, note));
    }

    @GetMapping({"/api/v1/payment-evidences/{id}/file", "/api/payment-evidences/{id}/file"})
    @Operation(summary = "View an authorized payment-evidence image")
    public ResponseEntity<byte[]> viewPaymentEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id) {
        PaymentEvidenceFileResponse file = paymentRequestService.getPaymentEvidenceFile(
                id, principal.getUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.content());
    }

    @GetMapping({"/api/v1/bookings/{bookingId}/payment-history", "/api/bookings/{bookingId}/payment-history"})
    @Operation(summary = "Get confirmed payment transaction history for a booking")
    public ApiResponse<List<ProviderTransactionResponse>> getBookingPaymentHistory(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long bookingId) {
        return ApiResponse.success(paymentRequestService.getBookingPaymentHistory(bookingId, principal.getUserId()));
    }

    // ==========================================
    // 2. Provider Confirmation (Provider only)
    // ==========================================

    @GetMapping({"/api/v1/provider/payment-requests", "/api/provider/payment-requests"})
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "Provider views incoming payment requests across their bookings")
    public ApiResponse<PageResponse<PaymentRequestResponse>> listProviderPaymentRequests(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int effectiveLimit = (size != null && size > 0) ? size : limit;
        int effectiveOffset = (page != null && page >= 0) ? page * effectiveLimit : offset;
        return ApiResponse.success(paymentRequestService.listProviderPaymentRequests(
                principal.getUserId(), status, effectiveOffset, effectiveLimit));
    }

    @PostMapping({"/api/v1/provider/payment-requests/{id}/confirm", "/api/provider/payment-requests/{id}/confirm"})
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "Provider confirms receipt of bank transfer money")
    public ApiResponse<PaymentRequestResponse> confirmPayment(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @RequestBody(required = false) @Valid ConfirmPaymentRequest request) {
        ConfirmPaymentRequest effectiveRequest = (request != null)
                ? request
                : new ConfirmPaymentRequest(null, null, null);
        return ApiResponse.success(paymentRequestService.confirmPayment(id, principal.getUserId(), effectiveRequest));
    }

    @PostMapping({"/api/v1/provider/payment-requests/{id}/reject", "/api/provider/payment-requests/{id}/reject"})
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "Provider rejects payment proof with reason")
    public ApiResponse<PaymentRequestResponse> rejectPayment(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @Valid @RequestBody RejectPaymentRequest request) {
        return ApiResponse.success(paymentRequestService.rejectPayment(id, principal.getUserId(), request));
    }

    @PostMapping({"/api/v1/provider/payment-requests/{id}/report-issue", "/api/provider/payment-requests/{id}/report-issue"})
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "Provider reports discrepancy or issue on payment request")
    public ApiResponse<PaymentRequestResponse> reportIssue(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ReportIssueRequest request) {
        return ApiResponse.success(paymentRequestService.reportIssue(id, principal.getUserId(), request));
    }

    // ==========================================
    // 3. Admin Sandbox Simulation (Admin only)
    // ==========================================

    @PostMapping({"/api/v1/admin/sandbox/payment-requests/{id}/simulate", "/api/admin/sandbox/payment-requests/{id}/simulate"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin simulates various payment scenarios in Sandbox (DEV/TEST only)")
    public ApiResponse<SimulatePaymentResponse> simulatePayment(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @Valid @RequestBody SimulatePaymentRequest request) {
        return ApiResponse.success(paymentRequestService.simulatePayment(id, principal.getUserId(), request));
    }

    @PostMapping({"/api/v1/admin/sandbox/seed", "/api/admin/sandbox/seed"})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin seeds demo bank accounts and data idempotently for sandbox testing")
    public ApiResponse<String> seedSandbox(
            @AuthenticationPrincipal SecurityUser principal) {
        paymentRequestService.seedSandboxData(principal.getUserId());
        return ApiResponse.success("Dữ liệu thử nghiệm Sandbox đã được khởi tạo thành công");
    }
}
