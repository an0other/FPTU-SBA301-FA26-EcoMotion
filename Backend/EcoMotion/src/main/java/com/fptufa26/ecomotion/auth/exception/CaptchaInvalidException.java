package com.fptufa26.ecomotion.auth.exception;

public class CaptchaInvalidException extends RuntimeException {

    public static final String MESSAGE = "Captcha verification failed";

    public CaptchaInvalidException() {
        super(MESSAGE);
    }

    public CaptchaInvalidException(String message) {
        super(message);
    }
}
