package com.fptufa26.ecomotion.auth.exception;

/** Raised when an email has no account waiting to be activated (unknown, locked or already active). */
public class NoPendingActivationException extends RuntimeException {

    public static final String MESSAGE = "No pending activation found for this email";

    public NoPendingActivationException() {
        super(MESSAGE);
    }
}
