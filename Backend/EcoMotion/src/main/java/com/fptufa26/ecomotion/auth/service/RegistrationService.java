package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.RegisterRequest;
import com.fptufa26.ecomotion.auth.dto.response.RegisterResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyOtpResponse;

public interface RegistrationService {

    SendOtpResponse sendOtp(String email);

    VerifyOtpResponse verifyOtp(String email, String otp);

    RegisterResponse register(RegisterRequest request, String ipAddress, String userAgent);
}
