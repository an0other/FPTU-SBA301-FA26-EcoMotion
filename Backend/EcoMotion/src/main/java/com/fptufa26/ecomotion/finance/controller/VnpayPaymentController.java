package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest;
import com.fptufa26.ecomotion.finance.dto.response.VnpayCallbackResultResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayIpnResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse;
import com.fptufa26.ecomotion.finance.service.VnpayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/payment/vnpay", "/api/payment/vnpay", "/v1/payment/vnpay", "/payment/vnpay"})
@RequiredArgsConstructor
@Tag(name = "VNPAY Payment Gateway", description = "VNPAY Sandbox payment url generation, IPN webhook, and callback verification")
public class VnpayPaymentController {

    private final VnpayService vnpayService;

    @PostMapping({"/create-url", "/create-payment-url"})
    @Operation(summary = "Generate VNPAY payment url for deposit or settlement")
    public ApiResponse<VnpayPaymentUrlResponse> createPaymentUrl(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody CreateVnpayPaymentUrlRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        Long userId = principal != null ? principal.getUserId() : null;
        VnpayPaymentUrlResponse response = vnpayService.createPaymentUrl(userId, request, clientIp);
        return ApiResponse.success(response);
    }

    @GetMapping({"/settlement/{bookingCode}", "/settlements/{bookingCode}"})
    @Operation(summary = "Get settlement and final payable calculation for a booking")
    public ApiResponse<com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse> getSettlement(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode) {
        Long userId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success(vnpayService.getSettlementDetails(userId, bookingCode));
    }

    @GetMapping("/ipn")
    @Operation(summary = "VNPAY IPN webhook receiver (GET)")
    public ResponseEntity<VnpayIpnResponse> receiveIpnGet(@RequestParam Map<String, String> allParams) {
        VnpayIpnResponse response = vnpayService.processIpn(allParams);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ipn")
    @Operation(summary = "VNPAY IPN webhook receiver (POST)")
    public ResponseEntity<VnpayIpnResponse> receiveIpnPost(@RequestParam Map<String, String> allParams) {
        VnpayIpnResponse response = vnpayService.processIpn(allParams);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    @Operation(summary = "Process browser return from VNPAY")
    public ApiResponse<VnpayCallbackResultResponse> processCallback(@RequestParam Map<String, String> allParams) {
        VnpayCallbackResultResponse response = vnpayService.processCallback(allParams);
        return ApiResponse.success(response);
    }

    @GetMapping("/query")
    @Operation(summary = "Query status of VNPAY transaction")
    public ApiResponse<VnpayCallbackResultResponse> queryTransaction(
            @RequestParam String bookingCode,
            @RequestParam String vnpTxnRef) {
        VnpayCallbackResultResponse response = vnpayService.queryTransaction(bookingCode, vnpTxnRef);
        return ApiResponse.success(response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
