package com.fptufa26.ecomotion.auth.exception;

public class AccountAlreadyActiveException extends RuntimeException {

    public static final String MESSAGE = "Account is already active";

    public AccountAlreadyActiveException() {
        super(MESSAGE);
    }
}
