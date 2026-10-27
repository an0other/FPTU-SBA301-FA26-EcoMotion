package com.fptufa26.ecomotion.notification.external.service;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;

public interface ExternalNotificationDispatcherService {

    NotificationResult sendOtp(OtpNotificationRequest request);

    NotificationResult sendBookingPlaced(BookingNotificationRequest request);

    NotificationResult sendDepositPaid(BookingNotificationRequest request);
}
