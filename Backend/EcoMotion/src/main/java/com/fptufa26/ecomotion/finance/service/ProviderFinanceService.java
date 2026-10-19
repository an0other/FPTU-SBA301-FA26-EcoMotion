package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.common.response.PageResponse;
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

import java.util.List;

public interface ProviderFinanceService {

    // Bank Account Management
    ProviderBankAccountResponse getBankAccount(Long currentUserId);

    List<ProviderBankAccountResponse> listBankAccounts(Long currentUserId);

    ProviderBankAccountResponse createBankAccount(Long currentUserId, CreateBankAccountRequest request);

    ProviderBankAccountResponse patchBankAccount(Long currentUserId, Long bankAccountId, PatchBankAccountRequest request);

    ProviderBankAccountResponse updateBankAccount(Long currentUserId, UpdateBankAccountRequest request);

    // Direct Payment Finance Summaries & Ledger
    ProviderFinanceSummaryResponse getFinanceSummary(Long currentUserId);

    PageResponse<ProviderTransactionResponse> listTransactions(Long currentUserId, String type, int offset, int limit);

    BookingFinancialBreakdownResponse getBookingBreakdown(Long currentUserId, String bookingCodeOrId);

    ProviderCommissionSummaryResponse getCommissionSummary(Long currentUserId);

    // Legacy Payout Methods (Kept for backward compatibility)
    PageResponse<ProviderPayoutSummaryResponse> listPayouts(Long currentUserId, String status, int offset, int limit);

    ProviderPayoutDetailResponse getPayoutDetail(Long currentUserId, Long payoutId);

    PaymentTransaction createMockPayout(Long bookingId);
}
