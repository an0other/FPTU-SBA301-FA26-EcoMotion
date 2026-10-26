package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.DriverRejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DriverRejectRequest(
        @NotNull(message = "Lý do từ chối là bắt buộc")
        DriverRejectReason rejectionReason,

        @Size(max = 500, message = "Ghi chú từ chối không vượt quá 500 ký tự")
        String rejectionNote
) {
}
