package com.fptufa26.ecomotion.auth.otp;

/**
 * Transport for delivering a registration OTP. Implementations are selected via
 * {@code app.otp.provider}: {@code smtp} delivers a real email (the email-existence gate),
 * {@code logging} just writes the code to the application log for local/test use.
 */
public interface OtpSender {

    void send(String email, String phone, String code);
}
