package com.fptufa26.ecomotion.auth.exception;

/** Raised when the user tries to activate an account that is already ACTIVE. */
public class AccountAlreadyActiveException extends RuntimeException {

    public static final String MESSAGE = "Account is already active";

    public AccountAlreadyActiveException() {
        super(MESSAGE);
    }
}
