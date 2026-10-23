package com.fptufa26.ecomotion.otp.exception;

public class OtpAttemptsExceededException extends RuntimeException {

    public static final String MESSAGE = "Too many OTP attempts";

    public OtpAttemptsExceededException() {
        super(MESSAGE);
    }
}
