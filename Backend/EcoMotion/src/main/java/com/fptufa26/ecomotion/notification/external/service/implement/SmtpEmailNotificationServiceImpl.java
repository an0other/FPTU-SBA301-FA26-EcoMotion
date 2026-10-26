package com.fptufa26.ecomotion.notification.external.service.implement;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.service.OtpExternalNotificationService;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.notification.external.email.mode", havingValue = "smtp")
public class SmtpEmailNotificationServiceImpl implements OtpExternalNotificationService {

    private final JavaMailSender mailSender;
    private final Validator validator;
    private final String from;

    public SmtpEmailNotificationServiceImpl(
            JavaMailSender mailSender,
            Validator validator,
            @Value("${app.mail.from:no-reply@ecomotion.vn}") String from) {
        this.mailSender = mailSender;
        this.validator = validator;
        this.from = from;
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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.recipient());
        message.setSubject("EcoMotion verification code");
        message.setText(buildOtpContent(request));

        try {
            mailSender.send(message);
            return new NotificationResult(true, null);
        } catch (MailException ex) {
            log.warn("Failed to send email notification", ex);
            return new NotificationResult(false, "Could not send email notification");
        }
    }

    private String buildOtpContent(OtpNotificationRequest request) {
        return """
                Your EcoMotion verification code is: %s
                This code expires in %d seconds.
                Never share this code with anyone.
                """.formatted(request.code(), request.expiresInSeconds());
    }

    private record EmailAddress(@NotBlank @Email String value) {
    }
}
