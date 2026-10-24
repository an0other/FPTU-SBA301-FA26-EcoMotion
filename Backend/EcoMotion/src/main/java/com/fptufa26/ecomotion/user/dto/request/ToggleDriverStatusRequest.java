package com.fptufa26.ecomotion.user.dto.request;

import com.fptufa26.ecomotion.user.enums.DriverStatus;
import jakarta.validation.constraints.NotNull;

public record ToggleDriverStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        DriverStatus status
) {
}
