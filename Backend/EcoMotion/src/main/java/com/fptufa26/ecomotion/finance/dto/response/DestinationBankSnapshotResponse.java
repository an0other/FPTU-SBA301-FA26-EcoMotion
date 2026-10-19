package com.fptufa26.ecomotion.finance.dto.response;

public record DestinationBankSnapshotResponse(
        String bankCode,
        String bankName,
        String accountNumberMasked,
        String accountHolderName
) {
}
