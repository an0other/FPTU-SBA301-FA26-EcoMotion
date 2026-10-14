package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.SendPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.request.VerifyPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.response.ActivationStatusResponse;
import com.fptufa26.ecomotion.auth.dto.response.ResumeActivationResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyPhoneOtpResponse;

public interface ActivationService {

    SendPhoneOtpResponse sendPhoneOtp(SendPhoneOtpRequest request);

    VerifyPhoneOtpResponse verifyPhoneOtp(VerifyPhoneOtpRequest request);

    ActivationStatusResponse status(String activationToken);

    SendOtpResponse requestResumeOtp(String email);

    ResumeActivationResponse resume(String email, String otp);
}
