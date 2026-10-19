package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBankAccountRequest(
        @NotBlank(message = "Mã ngân hàng không được để trống")
        @Size(max = 20, message = "Mã ngân hàng tối đa 20 ký tự")
        String bankCode,

        @NotBlank(message = "Số tài khoản không được để trống")
        @Size(max = 50, message = "Số tài khoản tối đa 50 ký tự")
        String accountNumber,

        @NotBlank(message = "Tên chủ tài khoản không được để trống")
        @Size(max = 150, message = "Tên chủ tài khoản tối đa 150 ký tự")
        String accountHolderName
) {
    @Override
    public String toString() {
        String masked = (accountNumber == null || accountNumber.length() <= 4)
                ? "****"
                : "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
        return "UpdateBankAccountRequest[bankCode=" + bankCode + ", accountNumber=" + masked + ", accountHolderName=" + accountHolderName + "]";
    }
}
