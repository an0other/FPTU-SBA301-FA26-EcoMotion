package com.fptufa26.ecomotion.notification.external.service.implement;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.service.BookingExternalNotificationService;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.service.OtpExternalNotificationService;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.notification.external.email.mode", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailNotificationServiceImpl implements OtpExternalNotificationService, BookingExternalNotificationService {

    private final Validator validator;

    public LoggingEmailNotificationServiceImpl(Validator validator) {
        this.validator = validator;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public NotificationResult sendOtp(OtpNotificationRequest request) {
        if (request == null || request.channel() != channel()
                || !validator.validate(request).isEmpty()
                || !validator.validate(new EmailAddress(request.recipient())).isEmpty()) {
            return new NotificationResult(false, "A valid email notification request is required");
        }
        log.info("Simulated OTP email notification to {}: code={}, expiresInSeconds={}",
                request.recipient(), request.code(), request.expiresInSeconds());
        return new NotificationResult(true, null);
    }

    @Override
    public NotificationResult sendBookingPlaced(BookingNotificationRequest request) {
        return logBooking(request, false);
    }

    @Override
    public NotificationResult sendDepositPaid(BookingNotificationRequest request) {
        return logBooking(request, true);
    }

    private NotificationResult logBooking(BookingNotificationRequest request, boolean depositPaid) {
        if (request == null || request.channel() != channel()
                || !validator.validate(request).isEmpty()
                || !validator.validate(new EmailAddress(request.recipient())).isEmpty()) {
            return new NotificationResult(false, "A valid booking email notification request is required");
        }
        log.info("Simulated booking email notification: bookingCode={}, depositPaid={}", request.bookingCode(), depositPaid);
        return new NotificationResult(true, null);
    }

    private record EmailAddress(@NotBlank @Email String value) {
    }
}
