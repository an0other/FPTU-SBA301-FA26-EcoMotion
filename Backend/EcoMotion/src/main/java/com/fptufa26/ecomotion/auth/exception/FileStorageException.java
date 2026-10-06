package com.fptufa26.ecomotion.auth.exception;

/** Raised when an uploaded file cannot be accepted or written to disk. */
public class FileStorageException extends RuntimeException {

    public static final String MESSAGE = "Could not store the uploaded file";

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
