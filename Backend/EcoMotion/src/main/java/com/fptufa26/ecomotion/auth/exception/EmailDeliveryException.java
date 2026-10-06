package com.fptufa26.ecomotion.auth.exception;

/**
 * Raised when the registration OTP email cannot be delivered. This is the email-existence gate:
 * a rejected/nonexistent mailbox aborts the flow before any user row is written.
 */
public class EmailDeliveryException extends RuntimeException {

    public static final String MESSAGE = "Could not deliver email to the provided address";

    public EmailDeliveryException() {
        super(MESSAGE);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
