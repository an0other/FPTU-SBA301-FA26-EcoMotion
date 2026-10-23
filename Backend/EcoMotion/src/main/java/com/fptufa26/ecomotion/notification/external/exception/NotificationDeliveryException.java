package com.fptufa26.ecomotion.notification.external.exception;

public class NotificationDeliveryException extends RuntimeException {

    public static final String MESSAGE = "Could not deliver notification to the provided recipient";

    public NotificationDeliveryException() {
        super(MESSAGE);
    }

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
