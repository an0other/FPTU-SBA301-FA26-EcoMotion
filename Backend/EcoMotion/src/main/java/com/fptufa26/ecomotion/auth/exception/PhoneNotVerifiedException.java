package com.fptufa26.ecomotion.auth.exception;

public class PhoneNotVerifiedException extends RuntimeException {

    public static final String MESSAGE = "Phone number must be verified before submitting identity documents";

    public PhoneNotVerifiedException() {
        super(MESSAGE);
    }
}
