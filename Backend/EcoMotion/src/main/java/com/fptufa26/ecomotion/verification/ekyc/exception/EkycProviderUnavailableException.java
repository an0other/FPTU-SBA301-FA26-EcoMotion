package com.fptufa26.ecomotion.verification.ekyc.exception;

public class EkycProviderUnavailableException extends EkycFailedException {

    public EkycProviderUnavailableException(String message) {
        super(message);
    }

    public EkycProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
