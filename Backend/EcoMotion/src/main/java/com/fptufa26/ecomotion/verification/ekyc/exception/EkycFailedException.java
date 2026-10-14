package com.fptufa26.ecomotion.verification.ekyc.exception;

public class EkycFailedException extends RuntimeException {

    public static final String MESSAGE = "eKYC verification failed";

    public EkycFailedException() {
        super(MESSAGE);
    }

    public EkycFailedException(String message) {
        super(message);
    }

    public EkycFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
