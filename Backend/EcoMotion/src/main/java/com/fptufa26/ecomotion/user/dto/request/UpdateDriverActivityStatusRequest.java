package com.fptufa26.ecomotion.user.dto.request;

import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDriverActivityStatusRequest(
        @NotNull(message = "Trạng thái hoạt động không được để trống")
        DriverActivityStatus activityStatus
) {
}
