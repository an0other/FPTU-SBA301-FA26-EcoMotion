package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;

public record RefundPreviewResponse(
        boolean cancellable,
        BigDecimal ratio,
        BigDecimal amount,
        String explanation
) {
}
