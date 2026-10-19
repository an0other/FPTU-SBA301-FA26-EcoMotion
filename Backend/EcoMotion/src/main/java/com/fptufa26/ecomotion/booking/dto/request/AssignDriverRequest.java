package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(
        @NotNull(message = "Vui lòng chọn tài xế")
        Long driverId
) {
}
