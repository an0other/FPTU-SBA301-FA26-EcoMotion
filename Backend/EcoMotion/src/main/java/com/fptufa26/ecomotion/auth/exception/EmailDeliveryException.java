package com.fptufa26.ecomotion.auth.exception;

public class EmailDeliveryException extends RuntimeException {

    public static final String MESSAGE = "Could not deliver email to the provided address";

    public EmailDeliveryException() {
        super(MESSAGE);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
