package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.ChargeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddChargeRequest(
        @NotNull(message = "Loại phụ phí không được để trống")
        ChargeType chargeType,

        @NotBlank(message = "Mô tả phụ phí không được để trống")
        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        Integer quantity,

        @NotNull(message = "Đơn giá không được để trống")
        @DecimalMin(value = "0.0", message = "Đơn giá phải lớn hơn hoặc bằng 0")
        BigDecimal unitPrice,

        BigDecimal appliedRate,

        @NotNull(message = "Số tiền không được để trống")
        @DecimalMin(value = "0.0", message = "Số tiền phải lớn hơn hoặc bằng 0")
        BigDecimal amount,

        @Size(max = 500, message = "Đường dẫn bằng chứng tối đa 500 ký tự")
        String evidenceUrl
) {
}
