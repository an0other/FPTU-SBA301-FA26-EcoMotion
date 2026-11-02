package com.fptufa26.ecomotion.verification.documentai;

public class DocumentAiUnavailableException extends RuntimeException {

    public DocumentAiUnavailableException(String message) {
        super(message);
    }

    public DocumentAiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
