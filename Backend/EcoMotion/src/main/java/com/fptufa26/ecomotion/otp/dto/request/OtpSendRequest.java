package com.fptufa26.ecomotion.otp.dto.request;

import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;

public record OtpSendRequest(
        String key,
        String recipient,
        OtpPurpose purpose,
        NotificationChannel channel
) {
}
