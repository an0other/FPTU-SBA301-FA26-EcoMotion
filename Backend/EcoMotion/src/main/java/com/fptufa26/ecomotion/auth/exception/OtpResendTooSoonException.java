package com.fptufa26.ecomotion.auth.exception;

public class OtpResendTooSoonException extends RuntimeException {

    public static final String MESSAGE = "Please wait before requesting a new OTP";

    public OtpResendTooSoonException() {
        super(MESSAGE);
    }
}
