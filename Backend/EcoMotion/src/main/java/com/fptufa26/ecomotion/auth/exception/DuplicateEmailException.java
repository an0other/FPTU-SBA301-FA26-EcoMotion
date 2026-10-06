package com.fptufa26.ecomotion.auth.exception;

public class DuplicateEmailException extends RuntimeException {

    public static final String MESSAGE = "Email already exists";

    public DuplicateEmailException() {
        super(MESSAGE);
    }
}
