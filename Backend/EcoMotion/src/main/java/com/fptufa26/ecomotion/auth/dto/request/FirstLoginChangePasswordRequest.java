package com.fptufa26.ecomotion.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstLoginChangePasswordRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        String email,

        @NotBlank(message = "Mật khẩu tạm thời không được để trống")
        @Size(max = 128, message = "Mật khẩu tạm thời không hợp lệ")
        String temporaryPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, max = 128, message = "Mật khẩu mới phải có từ 8 đến 128 ký tự")
        String newPassword
) {
}

