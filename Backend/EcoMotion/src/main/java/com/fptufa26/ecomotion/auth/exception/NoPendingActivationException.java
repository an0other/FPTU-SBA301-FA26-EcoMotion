package com.fptufa26.ecomotion.auth.exception;

public class NoPendingActivationException extends RuntimeException {

    public static final String MESSAGE = "No pending activation found for this email";

    public NoPendingActivationException() {
        super(MESSAGE);
    }
}
