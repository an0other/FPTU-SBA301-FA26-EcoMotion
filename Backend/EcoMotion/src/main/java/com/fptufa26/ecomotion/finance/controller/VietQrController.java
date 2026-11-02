package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.VietQrTransactionSyncRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;
import com.fptufa26.ecomotion.finance.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class VietQrController {
    private final VietQrPaymentService payments;
    private final VietQrCallbackAuthService authentication;
    private final VietQrTransactionSyncService sync;

    public record CreateRequest(@NotNull Long paymentRequestId) {}

    @GetMapping({"/api/v1/provider/vietqr/callbacks", "/api/v1/admin/vietqr/callbacks"})
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    public ApiResponse<org.springframework.data.domain.Page<com.fptufa26.ecomotion.finance.dto.response.VietQrCallbackResponse>> callbacks(
            @AuthenticationPrincipal SecurityUser user, @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success(payments.callbacks(user.getUserId(), page));
    }

    @PostMapping("/api/v1/payments/vietqr/create")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<PaymentRequestResponse> create(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody CreateRequest request) {
        return ApiResponse.success(payments.create(request.paymentRequestId(), user.getUserId()));
    }

    @GetMapping("/api/v1/payments/{paymentCode}/status")
    public ApiResponse<PaymentRequestResponse> status(@AuthenticationPrincipal SecurityUser user, @PathVariable String paymentCode) {
        return ApiResponse.success(payments.status(paymentCode, user.getUserId()));
    }

    @PostMapping("/api/v1/integrations/vietqr/token_generate")
    public Map<String, Object> token(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authentication.issueToken(authorization);
    }

    @PostMapping("/api/v1/integrations/vietqr/transaction-sync")
    public Map<String, Object> sync(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @Valid @RequestBody VietQrTransactionSyncRequest request) {
        authentication.authenticate(authorization);
        sync.synchronize(request);
        return Map.of("error", false, "errorReason", "", "toastMessage", "Đã tiếp nhận để đối soát",
                "object", Map.of("reftransactionid", request.transactionid()));
    }
}
