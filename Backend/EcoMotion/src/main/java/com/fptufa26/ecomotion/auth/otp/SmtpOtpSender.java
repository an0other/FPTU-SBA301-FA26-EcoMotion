package com.fptufa26.ecomotion.auth.otp;

import com.fptufa26.ecomotion.auth.exception.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the registration OTP as a real email. This is the "does the address actually exist" gate:
 * an SMTP rejection (e.g. nonexistent mailbox) surfaces as {@link EmailDeliveryException}, which
 * aborts registration before any user row is written.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "smtp")
public class SmtpOtpSender implements OtpSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpOtpSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@ecomotion.vn}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String email, String phone, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("EcoMotion - Ma xac thuc dang ky / Registration verification code");
        message.setText(buildBody(code));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.warn("Failed to deliver registration OTP to {}: {}", email, ex.getMessage());
            throw new EmailDeliveryException(
                    "Could not deliver the verification email to " + email, ex);
        }
    }

    private String buildBody(String code) {
        return """
                Xin chao,

                Ma xac thuc (OTP) dang ky tai khoan EcoMotion cua ban la: %s

                Ma co hieu luc trong vai phut. Vui long khong chia se ma nay voi bat ky ai.

                Hello,

                Your EcoMotion account registration verification code (OTP) is: %s

                The code expires shortly. Never share this code with anyone.

                Tran trong,
                EcoMotion
                """.formatted(code, code);
    }
}
