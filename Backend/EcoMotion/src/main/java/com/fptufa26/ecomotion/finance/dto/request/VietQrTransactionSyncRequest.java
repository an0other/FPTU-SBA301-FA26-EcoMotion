package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record VietQrTransactionSyncRequest(
        @NotBlank @Size(max = 100) String transactionid,
        @NotNull @Positive Long transactiontime,
        @NotBlank @Size(max = 100) String referencenumber,
        @NotNull @Positive @Digits(integer = 12, fraction = 0) BigDecimal amount,
        @NotBlank @Size(max = 500) String content,
        @NotBlank @Size(max = 100) String bankaccount,
        @NotBlank @Size(max = 19) String orderId,
        @NotBlank @Pattern(regexp = "[CD]") String transType) {
}
