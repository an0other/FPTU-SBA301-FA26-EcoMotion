package com.fptufa26.ecomotion.vehicle.exception;

public class MapLocationUnavailableException extends RuntimeException {
    public MapLocationUnavailableException(String message) {
        super(message);
    }

    public MapLocationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
