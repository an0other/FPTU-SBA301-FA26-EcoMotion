package com.fptufa26.ecomotion.common.exception;

import com.fptufa26.ecomotion.auth.exception.AccountAlreadyActiveException;
import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.AccountPendingException;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.CaptchaInvalidException;
import com.fptufa26.ecomotion.auth.exception.DuplicateEmailException;
import com.fptufa26.ecomotion.notification.external.exception.NotificationDeliveryException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.exception.InvalidRefreshTokenException;
import com.fptufa26.ecomotion.auth.exception.NoPendingActivationException;
import com.fptufa26.ecomotion.otp.exception.OtpAttemptsExceededException;
import com.fptufa26.ecomotion.otp.exception.OtpExpiredException;
import com.fptufa26.ecomotion.otp.exception.OtpInvalidException;
import com.fptufa26.ecomotion.otp.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.auth.exception.PhoneAlreadyExistsException;
import com.fptufa26.ecomotion.auth.exception.PhoneNotVerifiedException;
import com.fptufa26.ecomotion.auth.exception.RegistrationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.UnsupportedRoleException;
import com.fptufa26.ecomotion.common.cloudinary.CloudinaryInfrastructureException;
import com.fptufa26.ecomotion.booking.exception.BookingAccessDeniedException;
import com.fptufa26.ecomotion.booking.exception.BookingContractNotAllowedException;
import com.fptufa26.ecomotion.booking.exception.BookingContractNotFoundException;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.exception.DriverUnavailableException;
import com.fptufa26.ecomotion.booking.exception.InvalidBookingStateException;
import com.fptufa26.ecomotion.booking.exception.InvalidTripTransitionException;
import com.fptufa26.ecomotion.booking.exception.SettledChargeException;
import com.fptufa26.ecomotion.booking.exception.TripConflictException;
import com.fptufa26.ecomotion.booking.exception.VehicleUnavailableException;
import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.finance.exception.BankAccountConflictException;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountHolderNameException;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountNumberException;
import com.fptufa26.ecomotion.finance.exception.InvalidBankCodeException;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.exception.MissingBankAccountException;
import com.fptufa26.ecomotion.finance.exception.PaymentRequestNotFoundException;
import com.fptufa26.ecomotion.finance.exception.PayoutIneligibleException;
import com.fptufa26.ecomotion.finance.exception.PayoutNotFoundException;
import com.fptufa26.ecomotion.review.exception.ReviewAlreadyExistsException;
import com.fptufa26.ecomotion.review.exception.ReviewNotAllowedException;
import com.fptufa26.ecomotion.review.exception.ReviewNotFoundException;
import com.fptufa26.ecomotion.user.exception.CccdAlreadyExistsException;
import com.fptufa26.ecomotion.user.exception.DrivingLicenseAlreadyExistsException;
import com.fptufa26.ecomotion.user.exception.RenterProfileNotFoundException;
import com.fptufa26.ecomotion.vehicle.exception.MapLocationUnavailableException;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
            InvalidCredentialsException.class,
            InvalidRefreshTokenException.class,
            UsernameNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            Exception ex,
            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler({
            AccountInactiveException.class,
            AccountPendingException.class,
            BookingAccessDeniedException.class
    })
    public ResponseEntity<ErrorResponse> handleForbidden(
            Exception ex,
            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : "Access denied";
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", message, request);
    }

    @ExceptionHandler(CaptchaInvalidException.class)
    public ResponseEntity<ErrorResponse> handleCaptchaInvalid(
            CaptchaInvalidException ex,
            HttpServletRequest request) {
        log.warn("Captcha rejected on {} {}",
                request.getMethod(), request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining(", "));

        return build(
                HttpStatus.BAD_REQUEST,
                message.isEmpty() ? "Validation failed" : message,
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "Invalid value for parameter: " + ex.getName(),
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                request);
    }

    @ExceptionHandler({
            OtpInvalidException.class,
            RegistrationTokenInvalidException.class,
            ActivationTokenInvalidException.class,
            UnsupportedRoleException.class,
            InvalidBankCodeException.class,
            InvalidAccountNumberException.class,
            InvalidAccountHolderNameException.class,
            PayoutIneligibleException.class,
            InvalidPaymentOperationException.class,
            MissingBankAccountException.class,
            InvalidBookingStateException.class,
            BadRequestException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            Exception ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        log.warn("Rejected request on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorage(
            FileStorageException ex,
            HttpServletRequest request) {
        log.warn("File storage rejected on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({
            NoPendingActivationException.class,
            VehicleNotFoundException.class,
            BookingNotFoundException.class,
            ReviewNotFoundException.class,
            RenterProfileNotFoundException.class,
            BookingContractNotFoundException.class,
            PayoutNotFoundException.class,
            PaymentRequestNotFoundException.class,
            ResourceNotFoundException.class,
            NotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(
            Exception ex,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({
            DuplicateEmailException.class,
            PhoneAlreadyExistsException.class,
            CccdAlreadyExistsException.class,
            AccountAlreadyActiveException.class,
            PhoneNotVerifiedException.class,
            ReviewAlreadyExistsException.class,
            ReviewNotAllowedException.class,
            DrivingLicenseAlreadyExistsException.class,
            BookingContractNotAllowedException.class,
            VehicleUnavailableException.class,
            DriverUnavailableException.class,
            SettledChargeException.class,
            ConflictException.class
    })
    public ResponseEntity<ErrorResponse> handleResourceConflict(
            Exception ex,
            HttpServletRequest request) {
        log.warn("Resource conflict on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({
            BankAccountConflictException.class,
            InvalidTripTransitionException.class,
            TripConflictException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ResponseEntity<ErrorResponse> handleConcurrentConflict(
            Exception ex,
            HttpServletRequest request) {
        log.warn("Conflict or concurrent modification detected on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Resource was modified by another concurrent request.";

        return build(HttpStatus.CONFLICT, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}",
                request.getMethod(), request.getRequestURI());

        return build(HttpStatus.CONFLICT, "Resource already exists", request);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOtpExpired(
            OtpExpiredException ex,
            HttpServletRequest request) {
        return build(HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler({
            OtpAttemptsExceededException.class,
            OtpResendTooSoonException.class
    })
    public ResponseEntity<ErrorResponse> handleOtpRateLimit(
            Exception ex,
            HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(EkycFailedException.class)
    public ResponseEntity<ErrorResponse> handleEkycFailed(
            EkycFailedException ex,
            HttpServletRequest request) {
        log.warn("eKYC rejected on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(NotificationDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDelivery(
            NotificationDeliveryException ex,
            HttpServletRequest request) {
        log.error("Email delivery failed on {} {}",
                request.getMethod(), request.getRequestURI(), ex);

        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(MapLocationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMapLocationUnavailable(
            MapLocationUnavailableException ex,
            HttpServletRequest request) {
        log.warn("Map location service unavailable on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.toString());

        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Address lookup service unavailable",
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the maximum allowed size",
                request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingUploadPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "Missing multipart field: " + ex.getRequestPartName(),
                request);
    }

    @ExceptionHandler(CloudinaryInfrastructureException.class)
    public ResponseEntity<ErrorResponse> handleCloudinaryInfrastructure(
            CloudinaryInfrastructureException ex,
            HttpServletRequest request) {
        log.error("Cloudinary infrastructure failure on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(
                HttpStatus.BAD_GATEWAY,
                "CLOUDINARY_SERVICE_ERROR",
                "Dịch vụ lưu trữ hình ảnh Cloudinary tạm thời không khả dụng. Vui lòng thử lại sau.",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unhandled exception on {} {}",
                request.getMethod(), request.getRequestURI(), ex);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                request);
    }

    private String describeFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(
                        status,
                        message,
                        request.getRequestURI()));
    }
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(
                        status,
                        errorCode,
                        message,
                        request.getRequestURI()));
    }
}
