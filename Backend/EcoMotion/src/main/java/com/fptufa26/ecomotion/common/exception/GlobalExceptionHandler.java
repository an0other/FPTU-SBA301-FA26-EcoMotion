package com.fptufa26.ecomotion.common.exception;

import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.exception.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountInactiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountInactive(AccountInactiveException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private String describeFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
