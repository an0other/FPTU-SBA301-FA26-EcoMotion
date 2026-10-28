package com.fptufa26.ecomotion.notification.external.service;

import com.fptufa26.ecomotion.notification.external.dto.request.BookingNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;

public interface BookingExternalNotificationService extends ExternalNotificationService {

    NotificationResult sendBookingPlaced(BookingNotificationRequest request);

    NotificationResult sendDepositPaid(BookingNotificationRequest request);
}
