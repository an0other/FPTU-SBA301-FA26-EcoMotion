package com.fptufa26.ecomotion.notification.external.service;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;

public interface OtpExternalNotificationService extends ExternalNotificationService {

    NotificationResult sendOtp(OtpNotificationRequest request);
}
