package com.fptufa26.ecomotion.auth.exception;

/** Raised when the CCCD step is attempted before the phone number has been verified. */
public class PhoneNotVerifiedException extends RuntimeException {

    public static final String MESSAGE = "Phone number must be verified before submitting identity documents";

    public PhoneNotVerifiedException() {
        super(MESSAGE);
    }
}
