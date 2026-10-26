package com.fptufa26.ecomotion.admin.dto.request;

import com.fptufa26.ecomotion.user.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private UserStatus status;
}
