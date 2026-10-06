package com.fptufa26.ecomotion.auth.exception;

/**
 * Raised when the eKYC check fails: the CCCD could not be read, or the selfie does not match the
 * photo on the ID card. The account stays PENDING and the user may retry.
 */
public class EkycFailedException extends RuntimeException {

    public static final String MESSAGE = "eKYC verification failed";

    public EkycFailedException() {
        super(MESSAGE);
    }

    public EkycFailedException(String message) {
        super(message);
    }
}
