package com.fptufa26.ecomotion.auth.exception;

public class PhoneAlreadyExistsException extends RuntimeException {

    public static final String MESSAGE = "Phone number already exists";

    public PhoneAlreadyExistsException() {
        super(MESSAGE);
    }
}
