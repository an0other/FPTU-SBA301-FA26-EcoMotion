package com.fptufa26.ecomotion.admin.dto.request;

import com.fptufa26.ecomotion.user.enums.DriverStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DriverApprovalRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private DriverStatus status;
    private String rejectReason;
}
