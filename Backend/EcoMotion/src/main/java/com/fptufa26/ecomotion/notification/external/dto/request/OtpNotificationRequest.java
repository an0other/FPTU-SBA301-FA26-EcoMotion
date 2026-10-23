package com.fptufa26.ecomotion.notification.external.dto.request;

import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OtpNotificationRequest(
        @NotBlank String recipient,
        @NotBlank String code,
        @Positive long expiresInSeconds,
        @NotNull NotificationChannel channel
) {
}
