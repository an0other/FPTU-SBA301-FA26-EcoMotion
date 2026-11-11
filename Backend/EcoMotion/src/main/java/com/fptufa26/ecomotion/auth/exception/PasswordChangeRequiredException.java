package com.fptufa26.ecomotion.auth.exception;

public class PasswordChangeRequiredException extends RuntimeException {

    public static final String MESSAGE = "Password change required";

    public PasswordChangeRequiredException() {
        super(MESSAGE);
    }
}

