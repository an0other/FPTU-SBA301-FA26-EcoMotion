package com.fptufa26.ecomotion.auth.exception;

public class UnsupportedRoleException extends RuntimeException {

    public static final String MESSAGE = "Role is not allowed for registration";

    public UnsupportedRoleException() {
        super(MESSAGE);
    }
}
