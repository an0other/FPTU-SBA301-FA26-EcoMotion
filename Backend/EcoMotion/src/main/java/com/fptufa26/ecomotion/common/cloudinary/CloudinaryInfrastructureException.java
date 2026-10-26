package com.fptufa26.ecomotion.common.cloudinary;

public class CloudinaryInfrastructureException extends RuntimeException {

    public CloudinaryInfrastructureException(String message) {
        super(message);
    }

    public CloudinaryInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
