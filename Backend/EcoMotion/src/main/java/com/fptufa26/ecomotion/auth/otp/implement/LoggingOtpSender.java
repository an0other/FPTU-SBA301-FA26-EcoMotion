package com.fptufa26.ecomotion.auth.otp.implement;

import com.fptufa26.ecomotion.auth.otp.OtpSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(String email, String phone, String code) {
        log.info("Registration OTP for {} ({}) is {}", email, phone, code);
    }
}
