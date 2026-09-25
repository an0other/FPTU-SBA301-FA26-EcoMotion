package com.fptufa26.ecomotion.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public static final String MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
