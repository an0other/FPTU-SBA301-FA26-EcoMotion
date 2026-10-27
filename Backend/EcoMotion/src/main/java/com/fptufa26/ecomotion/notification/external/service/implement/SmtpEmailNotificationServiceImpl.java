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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.notification.external.email.mode", havingValue = "smtp")
public class SmtpEmailNotificationServiceImpl implements OtpExternalNotificationService, BookingExternalNotificationService {

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

    @Override
    public NotificationResult sendBookingPlaced(BookingNotificationRequest request) {
        return sendBooking(request, false);
    }

    @Override
    public NotificationResult sendDepositPaid(BookingNotificationRequest request) {
        return sendBooking(request, true);
    }

    private NotificationResult sendBooking(BookingNotificationRequest request, boolean depositPaid) {
        if (request == null || request.channel() != channel()
                || !validator.validate(request).isEmpty()
                || !validator.validate(new EmailAddress(request.recipient())).isEmpty()) {
            return new NotificationResult(false, "A valid booking email notification request is required");
        }
        String pickupTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(request.pickupTime());
        String amount = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
                .format(request.depositAmount());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.recipient());
        message.setSubject("EcoMotion - " + (depositPaid ? "Đã nhận tiền cọc " : "Đã tạo đơn thuê ") + request.bookingCode());
        message.setText("""
                %s

                Mã đơn: %s
                Xe: %s
                Giờ đón: %s (giờ Việt Nam)
                Điểm đón: %s
                Tiền cọc: %s VND
                """.formatted(depositPaid
                        ? "EcoMotion đã nhận đủ tiền cọc. Đơn thuê của bạn đã được xác nhận."
                        : "Đơn thuê của bạn đã được tạo. Vui lòng thanh toán cọc để xác nhận đơn.",
                request.bookingCode(), request.vehicleName(), pickupTime, request.pickupAddress(), amount));
        try {
            mailSender.send(message);
            return new NotificationResult(true, null);
        } catch (MailException ex) {
            log.warn("Failed to send booking email notification", ex);
            return new NotificationResult(false, "Could not send booking email notification");
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
