package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.finance.dto.request.CreateBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.request.PatchBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.request.UpdateBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.response.BookingFinancialBreakdownResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderBankAccountResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderCommissionSummaryResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderFinanceSummaryResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderPayoutDetailResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderPayoutSummaryResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderTransactionResponse;
import com.fptufa26.ecomotion.finance.service.ProviderFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/provider/finance", "/api/provider/finance"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
@Tag(name = "Provider Finance", description = "Provider finance overview, bank accounts, and transaction ledger (O11)")
public class ProviderFinanceController {

    private final ProviderFinanceService providerFinanceService;

    @GetMapping("/summary")
    @Operation(summary = "Get provider overall finance overview and metrics")
    public ApiResponse<ProviderFinanceSummaryResponse> getSummary(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(providerFinanceService.getFinanceSummary(principal.getUserId()));
    }

    @GetMapping("/bank-accounts")
    @Operation(summary = "List all bank accounts configured by provider")
    public ApiResponse<List<ProviderBankAccountResponse>> listBankAccounts(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(providerFinanceService.listBankAccounts(principal.getUserId()));
    }

    @PostMapping("/bank-accounts")
    @Operation(summary = "Create and activate a new provider receiving bank account")
    public ApiResponse<ProviderBankAccountResponse> createBankAccount(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody CreateBankAccountRequest request) {
        return ApiResponse.success(providerFinanceService.createBankAccount(principal.getUserId(), request));
    }

    @PatchMapping("/bank-accounts/{id}")
    @Operation(summary = "Partially update or activate/deactivate an existing bank account")
    public ApiResponse<ProviderBankAccountResponse> patchBankAccount(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @Valid @RequestBody PatchBankAccountRequest request) {
        return ApiResponse.success(providerFinanceService.patchBankAccount(principal.getUserId(), id, request));
    }

    @GetMapping("/transactions")
    @Operation(summary = "List provider incoming/outgoing financial transactions")
    public ApiResponse<PageResponse<ProviderTransactionResponse>> listTransactions(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int effectiveLimit = (size != null && size > 0) ? size : limit;
        int effectiveOffset = (page != null && page >= 0) ? page * effectiveLimit : offset;
        return ApiResponse.success(providerFinanceService.listTransactions(
                principal.getUserId(), type, effectiveOffset, effectiveLimit));
    }

    @GetMapping("/bookings/{bookingId}/breakdown")
    @Operation(summary = "Get detailed financial breakdown of a specific booking")
    public ApiResponse<BookingFinancialBreakdownResponse> getBookingBreakdown(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId) {
        return ApiResponse.success(providerFinanceService.getBookingBreakdown(principal.getUserId(), bookingId));
    }

    @GetMapping("/commissions")
    @Operation(summary = "Get platform commission overview and itemized records")
    public ApiResponse<ProviderCommissionSummaryResponse> getCommissions(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(providerFinanceService.getCommissionSummary(principal.getUserId()));
    }

    // ==========================================
    // Backward-Compatible Aliases
    // ==========================================

    @GetMapping("/bank-account")
    @Operation(summary = "View current active provider bank account (alias)")
    public ApiResponse<ProviderBankAccountResponse> getBankAccount(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(providerFinanceService.getBankAccount(principal.getUserId()));
    }

    @PutMapping("/bank-account")
    @Operation(summary = "Add or replace current receiving bank account (alias)")
    public ApiResponse<ProviderBankAccountResponse> updateBankAccount(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody UpdateBankAccountRequest request) {
        return ApiResponse.success(providerFinanceService.updateBankAccount(principal.getUserId(), request));
    }

    @GetMapping("/payouts")
    @Operation(summary = "Legacy payout ledger (backward compatibility)")
    public ApiResponse<PageResponse<ProviderPayoutSummaryResponse>> listPayouts(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int effectiveLimit = (size != null && size > 0) ? size : limit;
        int effectiveOffset = (page != null && page >= 0) ? page * effectiveLimit : offset;
        return ApiResponse.success(providerFinanceService.listPayouts(
                principal.getUserId(), status, effectiveOffset, effectiveLimit));
    }

    @GetMapping("/payouts/{payoutId}")
    @Operation(summary = "View payout detail by ID (backward compatibility)")
    public ApiResponse<ProviderPayoutDetailResponse> getPayoutDetail(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long payoutId) {
        return ApiResponse.success(providerFinanceService.getPayoutDetail(principal.getUserId(), payoutId));
    }
}
