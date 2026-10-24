package com.fptufa26.ecomotion.otp.exception;

public class OtpExpiredException extends RuntimeException {

    public static final String MESSAGE = "OTP has expired";

    public OtpExpiredException() {
        super(MESSAGE);
    }
}
