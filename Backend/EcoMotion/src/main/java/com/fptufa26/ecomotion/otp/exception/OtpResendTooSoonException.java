package com.fptufa26.ecomotion.otp.exception;

public class OtpResendTooSoonException extends RuntimeException {

    public static final String MESSAGE = "Please wait before requesting a new OTP";

    public OtpResendTooSoonException() {
        super(MESSAGE);
    }
}
