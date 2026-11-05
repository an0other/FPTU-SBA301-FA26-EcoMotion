package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RefundAccountRequest(
        @NotBlank(message = "Vui lòng nhập ngân hàng nhận hoàn")
        @Size(max = 150) String bankName,
        @NotBlank(message = "Vui lòng nhập tên chủ tài khoản")
        @Size(max = 150) String accountHolder,
        @NotBlank(message = "Vui lòng nhập số tài khoản nhận hoàn")
        @Pattern(regexp = "[0-9]{6,30}", message = "Số tài khoản phải gồm 6–30 chữ số") String accountNumber
) {}
