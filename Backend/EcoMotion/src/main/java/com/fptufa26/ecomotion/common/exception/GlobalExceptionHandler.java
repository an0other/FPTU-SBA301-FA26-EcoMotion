package com.fptufa26.ecomotion.common.exception;

import com.fptufa26.ecomotion.auth.exception.AccountAlreadyActiveException;
import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.AccountPendingException;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.CaptchaInvalidException;
import com.fptufa26.ecomotion.auth.exception.DuplicateEmailException;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import com.fptufa26.ecomotion.auth.exception.EmailDeliveryException;
import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.exception.InvalidRefreshTokenException;
import com.fptufa26.ecomotion.auth.exception.NoPendingActivationException;
import com.fptufa26.ecomotion.auth.exception.OtpAttemptsExceededException;
import com.fptufa26.ecomotion.auth.exception.OtpExpiredException;
import com.fptufa26.ecomotion.auth.exception.OtpInvalidException;
import com.fptufa26.ecomotion.auth.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.auth.exception.PhoneAlreadyExistsException;
import com.fptufa26.ecomotion.auth.exception.PhoneNotVerifiedException;
import com.fptufa26.ecomotion.auth.exception.RegistrationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.UnsupportedRoleException;
import com.fptufa26.ecomotion.user.exception.CccdAlreadyExistsException;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.vehicle.exception.MapLocationUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(AccountPendingException.class)
    public ResponseEntity<ErrorResponse> handleAccountPending(AccountPendingException ex,
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

    @ExceptionHandler(CaptchaInvalidException.class)
    public ResponseEntity<ErrorResponse> handleCaptchaInvalid(CaptchaInvalidException ex,
                                                             HttpServletRequest request) {
        log.warn("Captcha rejected on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex,
                                                              HttpServletRequest request) {
        log.warn("Duplicate email rejected on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(PhoneAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePhone(PhoneAlreadyExistsException ex,
                                                              HttpServletRequest request) {
        log.warn("Duplicate phone rejected on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(CccdAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateCccd(CccdAlreadyExistsException ex,
                                                             HttpServletRequest request) {
        log.warn("Duplicate CCCD rejected on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(OtpInvalidException.class)
    public ResponseEntity<ErrorResponse> handleOtpInvalid(OtpInvalidException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOtpExpired(OtpExpiredException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler(OtpAttemptsExceededException.class)
    public ResponseEntity<ErrorResponse> handleOtpAttempts(OtpAttemptsExceededException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(OtpResendTooSoonException.class)
    public ResponseEntity<ErrorResponse> handleOtpResend(OtpResendTooSoonException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(RegistrationTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationToken(RegistrationTokenInvalidException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ActivationTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleActivationToken(ActivationTokenInvalidException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(NoPendingActivationException.class)
    public ResponseEntity<ErrorResponse> handleNoPendingActivation(NoPendingActivationException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVehicleNotFound(VehicleNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountAlreadyActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountAlreadyActive(AccountAlreadyActiveException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(PhoneNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handlePhoneNotVerified(PhoneNotVerifiedException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(EkycFailedException.class)
    public ResponseEntity<ErrorResponse> handleEkycFailed(EkycFailedException ex,
                                                          HttpServletRequest request) {
        log.warn("eKYC rejected on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(FileStorageException ex,
                                                           HttpServletRequest request) {
        log.warn("File storage rejected on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedRoleException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedRole(UnsupportedRoleException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDelivery(EmailDeliveryException ex,
                                                             HttpServletRequest request) {
        log.error("Email delivery failed on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        log.warn("Rejected request on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MapLocationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMapLocationUnavailable(MapLocationUnavailableException ex,
                                                                        HttpServletRequest request) {
        log.warn("Map location service unavailable on {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.toString());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Address lookup service unavailable", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "Resource already exists", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(com.fptufa26.ecomotion.booking.exception.BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotFound(com.fptufa26.ecomotion.booking.exception.BookingNotFoundException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(com.fptufa26.ecomotion.booking.exception.BookingAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleBookingAccessDenied(com.fptufa26.ecomotion.booking.exception.BookingAccessDeniedException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(com.fptufa26.ecomotion.booking.exception.InvalidBookingStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBookingState(com.fptufa26.ecomotion.booking.exception.InvalidBookingStateException ex,
                                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(com.fptufa26.ecomotion.booking.exception.VehicleUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleVehicleUnavailable(com.fptufa26.ecomotion.booking.exception.VehicleUnavailableException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(com.fptufa26.ecomotion.booking.exception.DriverUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDriverUnavailable(com.fptufa26.ecomotion.booking.exception.DriverUnavailableException ex,
                                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(com.fptufa26.ecomotion.booking.exception.SettledChargeException.class)
    public ResponseEntity<ErrorResponse> handleSettledCharge(com.fptufa26.ecomotion.booking.exception.SettledChargeException ex,
                                                            HttpServletRequest request) {
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
