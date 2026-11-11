package com.fptufa26.ecomotion.notification.external.service;

import com.fptufa26.ecomotion.notification.external.dto.request.AccountCredentialNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;

public interface AccountExternalNotificationService extends ExternalNotificationService {

    NotificationResult sendDriverCredentials(AccountCredentialNotificationRequest request);
}

