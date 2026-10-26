package com.fptufa26.ecomotion.admin.dto.request;

import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DisputeResolveRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private DisputeStatus status;

    private DisputeResolutionDecision decision;

    @NotBlank(message = "Nội dung giải quyết không được để trống")
    private String resolution;
}
