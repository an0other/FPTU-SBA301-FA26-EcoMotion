package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DenyBookingRequest(
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 500, message = "Lý do từ chối tối đa 500 ký tự")
        String reason
) {
}
