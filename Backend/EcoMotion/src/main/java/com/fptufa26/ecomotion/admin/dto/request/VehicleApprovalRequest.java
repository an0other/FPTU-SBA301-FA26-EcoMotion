package com.fptufa26.ecomotion.admin.dto.request;

import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehicleApprovalRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private VehicleStatus status;
    private String reason;
}
