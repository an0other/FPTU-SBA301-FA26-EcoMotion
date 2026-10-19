package com.fptufa26.ecomotion.finance.dto.response;

import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import java.time.Instant;

public record ProviderBankAccountResponse(
        Long id,
        Long providerId,
        String bankCode,
        String bankName,
        String accountNumber,
        String accountNumberMasked,
        String accountHolderName,
        BankAccountStatus status,
        boolean verified,
        Instant createdAt,
        Instant updatedAt
) {
    // Backward-compatible constructor
    public ProviderBankAccountResponse(
            Long id,
            String bankCode,
            String bankName,
            String accountNumberMasked,
            String accountHolderName,
            boolean verified,
            Instant updatedAt
    ) {
        this(id, null, bankCode, bankName, accountNumberMasked, accountNumberMasked, accountHolderName,
                BankAccountStatus.ACTIVE, verified, updatedAt, updatedAt);
    }
}
