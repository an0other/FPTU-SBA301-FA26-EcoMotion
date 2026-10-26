package com.fptufa26.ecomotion.otp.dto.request;

import com.fptufa26.ecomotion.otp.enums.OtpPurpose;

public record OtpVerifyRequest(
        String key,
        String code,
        OtpPurpose purpose
) {
}
