package com.fptufa26.ecomotion.admin.dto.request;

import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProviderStatusRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private ProviderStatus status;
}
