package com.fptufa26.ecomotion.finance.dto.request;

import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import jakarta.validation.constraints.Size;

public record PatchBankAccountRequest(
        @Size(max = 20, message = "Mã ngân hàng tối đa 20 ký tự")
        String bankCode,

        @Size(max = 50, message = "Số tài khoản tối đa 50 ký tự")
        String accountNumber,

        @Size(max = 150, message = "Tên chủ tài khoản tối đa 150 ký tự")
        String accountHolderName,

        BankAccountStatus status
) {
    @Override
    public String toString() {
        String masked = (accountNumber == null || accountNumber.length() <= 4)
                ? (accountNumber == null ? "null" : "****")
                : "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
        return "PatchBankAccountRequest[bankCode=" + bankCode + ", accountNumber=" + masked + ", accountHolderName=" + accountHolderName + ", status=" + status + "]";
    }
}
