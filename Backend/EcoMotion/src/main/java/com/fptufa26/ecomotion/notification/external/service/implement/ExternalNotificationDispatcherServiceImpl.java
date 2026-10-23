package com.fptufa26.ecomotion.notification.external.service.implement;

import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationService;
import com.fptufa26.ecomotion.notification.external.service.OtpExternalNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class ExternalNotificationDispatcherServiceImpl implements ExternalNotificationDispatcherService {

    private final Map<NotificationChannel, ExternalNotificationService> services;
    private final List<NotificationChannel> fallbackOrder;

    public ExternalNotificationDispatcherServiceImpl(
            List<ExternalNotificationService> services,
            @Value("${app.notification.external.fallback-order:EMAIL,SMS}")
            List<NotificationChannel> fallbackOrder) {
        this.services = new EnumMap<>(NotificationChannel.class);
        for (ExternalNotificationService service : services) {
            ExternalNotificationService existing = this.services.putIfAbsent(service.channel(), service);
            if (existing != null) {
                throw new IllegalStateException(
                        "Multiple notification services configured for " + service.channel());
            }
        }
        if (fallbackOrder.isEmpty() || new HashSet<>(fallbackOrder).size() != fallbackOrder.size()) {
            throw new IllegalStateException("External notification fallback order must contain unique channels");
        }
        this.fallbackOrder = List.copyOf(fallbackOrder);
    }

    @Override
    public NotificationResult sendOtp(OtpNotificationRequest request) {
        if (request == null || request.channel() == null) {
            return new NotificationResult(false, "Notification channel is required");
        }
        for (NotificationChannel channel : fallbackOrder) {
            if (channel != request.channel()) {
                continue;
            }
            ExternalNotificationService service = services.get(channel);
            return service instanceof OtpExternalNotificationService otpService
                    ? otpService.sendOtp(request)
                    : unsupported(channel);
        }
        return unsupported(request.channel());
    }

    private NotificationResult unsupported(NotificationChannel channel) {
        return new NotificationResult(false, "No notification service configured for " + channel);
    }
}
