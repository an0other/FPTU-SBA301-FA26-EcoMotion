package com.fptufa26.ecomotion.admin.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.response.CommissionStatementResponse;
import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;
import com.fptufa26.ecomotion.finance.service.CommissionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/commission-statements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Commission", description = "Bảng kê hoa hồng tháng của nhà xe")
public class AdminCommissionController {

    private final CommissionService commissionService;

    @GetMapping
    @Operation(summary = "List commission statements, filtered by month (YYYY-MM) and status")
    public ApiResponse<List<CommissionStatementResponse>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) CommissionStatementStatus status) {
        return ApiResponse.success(commissionService.adminStatements(month, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<CommissionStatementResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(commissionService.adminStatement(id));
    }

    @GetMapping("/{id}/payments/{paymentId}/proof")
    @Operation(summary = "View the receipt image of one commission payment")
    public org.springframework.http.ResponseEntity<byte[]> proof(@PathVariable Long id, @PathVariable Long paymentId,
                                                               @RequestParam(defaultValue = "false") boolean dispute) {
        var file = commissionService.adminProof(id, paymentId, dispute);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(file.contentType()))
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(file.content());
    }

    @PostMapping("/{id}/payments/{paymentId}/resolve-dispute")
    @Operation(summary = "Resolve a provider's dispute about a confirmed commission amount")
    public ApiResponse<CommissionStatementResponse> resolveDispute(@AuthenticationPrincipal SecurityUser principal,
                                                                   @PathVariable Long id, @PathVariable Long paymentId,
                                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.success(commissionService.resolvePaymentDispute(principal.getUserId(), id, paymentId,
                Boolean.TRUE.equals(body.get("accept")), (String) body.get("note")));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm the commission amount actually received")
    public ApiResponse<CommissionStatementResponse> confirm(@AuthenticationPrincipal SecurityUser principal,
                                                            @PathVariable Long id,
                                                            @Valid @RequestBody ConfirmCommissionPaymentRequest request) {
        return ApiResponse.success(commissionService.confirmPayment(principal.getUserId(), id, request));
    }

    @PostMapping("/{id}/reject-report")
    @Operation(summary = "Payment not found in bank statement: return the statement to unpaid")
    public ApiResponse<CommissionStatementResponse> rejectReport(@PathVariable Long id,
                                                                 @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.success(commissionService.rejectReport(id, body == null ? null : body.get("note")));
    }

    @PostMapping("/generate")
    @Operation(summary = "Issue statements for a finished month now (defaults to last month)")
    public ApiResponse<Map<String, Object>> generate(@RequestParam(required = false) String month) {
        YearMonth target = month == null || month.isBlank() ? YearMonth.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusMonths(1) : YearMonth.parse(month.trim());
        int created = commissionService.generate(target);
        commissionService.markOverdue();
        return ApiResponse.success(Map.of("month", target.toString(), "created", created));
    }
}
