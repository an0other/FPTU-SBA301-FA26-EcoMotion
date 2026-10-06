package com.fptufa26.ecomotion.auth.exception;

public class ActivationTokenInvalidException extends RuntimeException {

    public static final String MESSAGE = "Activation token is invalid or has expired";

    public ActivationTokenInvalidException() {
        super(MESSAGE);
    }
}
