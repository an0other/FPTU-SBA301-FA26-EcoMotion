package com.fptufa26.ecomotion.finance.exception;

public class MissingBankAccountException extends RuntimeException {
    public MissingBankAccountException(String message) {
        super(message);
    }
}
