package com.fptufa26.ecomotion.auth.exception;

public class AccountInactiveException extends RuntimeException {

    public static final String MESSAGE = "Account is inactive";

    public AccountInactiveException() {
        super(MESSAGE);
    }
}
