package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.DriverAssignmentMode;

public record AssignDriverRequest(
        Long driverId,
        DriverAssignmentMode mode
) {
    public AssignDriverRequest(Long driverId) {
        this(driverId, DriverAssignmentMode.MANUAL);
    }

    public AssignDriverRequest {
        mode = mode == null ? DriverAssignmentMode.MANUAL : mode;
        if (mode == DriverAssignmentMode.MANUAL && driverId == null) {
            throw new IllegalArgumentException("Vui lòng chọn tài xế khi phân công thủ công");
        }
    }
}
