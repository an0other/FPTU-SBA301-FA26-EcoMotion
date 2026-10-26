package com.fptufa26.ecomotion.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundRequest {
    @NotNull(message = "Mã đơn không được để trống")
    private Long bookingId;

    private BigDecimal amount;

    private String reason;
}
