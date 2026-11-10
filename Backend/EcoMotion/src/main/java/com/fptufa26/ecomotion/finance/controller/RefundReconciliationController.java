package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.RefundReconcileRequest;
import com.fptufa26.ecomotion.finance.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class RefundReconciliationController {
    private final RefundReconciliationService reviews;
    private final RefundService refunds;
    @GetMapping
    public ApiResponse<List<RefundResponse>> list(@AuthenticationPrincipal SecurityUser user) {
        return ApiResponse.success(refunds.listRefundsForProvider(user.getUserId()));
    }
    @PostMapping("/{id}/reconcile")
    public ApiResponse<Void> reconcile(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                       @Valid @RequestBody RefundReconcileRequest request) {
        reviews.reconcile(user.getUserId(), id, request);
        return ApiResponse.success(null);
    }
    @GetMapping("/{id}/history")
    public ApiResponse<List<FinancialAuditService.Entry>> history(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        return ApiResponse.success(reviews.history(user.getUserId(), id));
    }
}
