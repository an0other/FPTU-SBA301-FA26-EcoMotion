package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.*;

public record RefundReconcileRequest(
        @NotBlank @Size(max = 80) String requestId,
        @NotNull @Min(0) Long version,
        @NotBlank @Pattern(regexp = "HOLD|TRANSFER_VERIFIED|FAILED_VERIFIED|WRONG_RECIPIENT") String outcome,
        @NotBlank @Size(max = 1000) String note,
        /** Tuỳ chọn: tham chiếu sao kê/chứng cứ admin đã kiểm tra. */
        @Size(max = 500) String evidenceReference,
        /** Tuỳ chọn: mã giao dịch chuyển hoàn; mặc định dùng mã nhà xe đã khai trên biên lai. */
        @Size(max = 100) String transactionReference) {}
