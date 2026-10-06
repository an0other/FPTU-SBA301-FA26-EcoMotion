package com.fptufa26.ecomotion.auth.exception;

public class CccdAlreadyExistsException extends RuntimeException {

    public static final String MESSAGE = "This CCCD number is already registered to another account";

    public CccdAlreadyExistsException() {
        super(MESSAGE);
    }
}
