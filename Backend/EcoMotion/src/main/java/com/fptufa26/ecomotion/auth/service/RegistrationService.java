package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.RegisterRequest;
import com.fptufa26.ecomotion.auth.dto.response.RegisterResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyOtpResponse;

public interface RegistrationService {

    /**
     * Sends a registration OTP to the address. Delivery failure (rejected/nonexistent mailbox)
     * raises {@code EmailDeliveryException} and no state is left behind.
     */
    SendOtpResponse sendOtp(String email);

    /** Verifies the OTP and returns a short-lived registration token that authorises creation. */
    VerifyOtpResponse verifyOtp(String email, String otp);

    /**
     * Creates the account in {@code PENDING} status and returns a short-lived activation token.
     * The account only becomes {@code ACTIVE} once the activation page finishes the phone
     * verification and the CCCD/eKYC check. A user row is only ever written after a successful
     * OTP send + verification, so a nonexistent mailbox can never produce an account.
     */
    RegisterResponse register(RegisterRequest request, String ipAddress, String userAgent);
}
