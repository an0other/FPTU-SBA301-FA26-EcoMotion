package com.fptufa26.ecomotion.admin.dto.request;

import com.fptufa26.ecomotion.user.enums.EkycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EkycStatusRequest {
    @NotNull(message = "Trạng thái eKYC không được để trống")
    private EkycStatus status;
}
