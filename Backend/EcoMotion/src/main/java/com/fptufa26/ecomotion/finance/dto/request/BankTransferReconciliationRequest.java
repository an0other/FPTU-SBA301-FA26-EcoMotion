package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Operator attestation against the receiving bank statement, never customer-uploaded proof alone. */
public record BankTransferReconciliationRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal receivedAmount,
        @NotBlank @Size(max = 100) String bankTransactionRef,
        @NotBlank @Size(max = 200) String bankStatementReference,
        @AssertTrue boolean bankReceiptVerified,
        @NotBlank @Size(max = 64) String reconciliationId,
        @NotNull @PastOrPresent Instant receivedAt,
        @Size(max = 500) String note
) {}
