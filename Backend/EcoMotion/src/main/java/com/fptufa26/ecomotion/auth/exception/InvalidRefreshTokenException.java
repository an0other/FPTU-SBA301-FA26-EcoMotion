package com.fptufa26.ecomotion.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public static final String MESSAGE = "Invalid or expired refresh token";

    public InvalidRefreshTokenException() {
        super(MESSAGE);
    }
}
