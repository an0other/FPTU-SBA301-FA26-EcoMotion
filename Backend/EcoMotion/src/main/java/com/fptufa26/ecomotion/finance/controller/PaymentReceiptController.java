package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.response.PaymentReceiptResponse;
import com.fptufa26.ecomotion.finance.service.PaymentReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentReceiptController {
    private final PaymentReceiptService paymentReceiptService;

    @GetMapping({"/api/v1/payments/receipt", "/api/payments/receipt"})
    public ResponseEntity<ApiResponse<PaymentReceiptResponse>> getReceipt(
            @AuthenticationPrincipal SecurityUser principal, @RequestParam String reference) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(paymentReceiptService.getReceipt(principal == null ? null : principal.getUserId(), reference)));
    }
}
