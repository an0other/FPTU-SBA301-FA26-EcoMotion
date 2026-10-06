package com.fptufa26.ecomotion.auth.exception;

public class RegistrationTokenInvalidException extends RuntimeException {

    public static final String MESSAGE = "Registration token is invalid or expired";

    public RegistrationTokenInvalidException() {
        super(MESSAGE);
    }
}
