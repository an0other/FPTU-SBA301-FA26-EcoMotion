package com.fptufa26.ecomotion.otp.service;

import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.dto.response.OtpChallenge;
import com.fptufa26.ecomotion.otp.dto.response.OtpVerificationResult;

public interface OtpService {

    OtpChallenge send(OtpSendRequest request);

    OtpVerificationResult verify(OtpVerifyRequest request);
}
