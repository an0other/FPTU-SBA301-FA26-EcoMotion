package com.fptufa26.ecomotion.common.storage;

public class FileStorageException extends RuntimeException {

    public static final String MESSAGE = "Could not store the uploaded file";

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
