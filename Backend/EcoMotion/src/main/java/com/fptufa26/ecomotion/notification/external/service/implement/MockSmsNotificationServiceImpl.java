package com.fptufa26.ecomotion.notification.external.service.implement;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.service.OtpExternalNotificationService;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MockSmsNotificationServiceImpl implements OtpExternalNotificationService {

    private final Validator validator;

    public MockSmsNotificationServiceImpl(Validator validator) {
        this.validator = validator;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public NotificationResult sendOtp(OtpNotificationRequest request) {
        if (request == null || request.channel() != channel() || !validator.validate(request).isEmpty()) {
            return new NotificationResult(false, "A valid SMS notification request is required");
        }
        String content = "EcoMotion OTP: %s. Expires in %d seconds."
                .formatted(request.code(), request.expiresInSeconds());
        log.info("Mock SMS notification to {}: {}", request.recipient(), content);
        return new NotificationResult(true, null);
    }
}
