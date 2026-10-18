package com.fptufa26.ecomotion.user.exception;

public class RenterProfileNotFoundException extends RuntimeException {

    public RenterProfileNotFoundException() {
        super("Renter profile not found");
    }
}