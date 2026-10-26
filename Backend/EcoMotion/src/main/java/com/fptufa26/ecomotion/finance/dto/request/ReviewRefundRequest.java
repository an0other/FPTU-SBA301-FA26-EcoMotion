package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewRefundRequest(
        @NotBlank(message = "Hành động là bắt buộc (APPROVE hoặc REJECT)")
        String action,

        @Size(max = 500, message = "Lý do từ chối không quá 500 ký tự")
        String rejectionReason
) {
}
