package com.fptufa26.ecomotion.auth.exception;

public class OtpInvalidException extends RuntimeException {

    public static final String MESSAGE = "Invalid OTP";

    public OtpInvalidException() {
        super(MESSAGE);
    }
}
