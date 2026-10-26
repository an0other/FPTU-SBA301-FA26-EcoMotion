package com.fptufa26.ecomotion.common.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String errorCode,
        String traceId
) {
    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, error, UUID.randomUUID().toString());
    }

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.name(), message, path, status.name(), UUID.randomUUID().toString());
    }

    public static ErrorResponse of(HttpStatus status, String errorCode, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.name(), message, path, errorCode, UUID.randomUUID().toString());
    }
}
