package com.fptufa26.ecomotion.user.exception;

public class DrivingLicenseAlreadyExistsException extends RuntimeException {

    public DrivingLicenseAlreadyExistsException() {
        super("Driving license number already exists");
    }
}