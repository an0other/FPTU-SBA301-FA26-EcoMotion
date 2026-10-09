package com.fptufa26.ecomotion.auth.exception;

public class AccountPendingException extends RuntimeException {

    public static final String MESSAGE = "Account is pending activation";

    public AccountPendingException() {
        super(MESSAGE);
    }
}
