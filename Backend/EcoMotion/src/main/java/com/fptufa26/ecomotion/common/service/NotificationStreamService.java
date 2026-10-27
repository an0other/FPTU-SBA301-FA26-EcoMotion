package com.fptufa26.ecomotion.common.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationStreamService {

    void registerEmitter(Long userId, SseEmitter emitter);

    void removeEmitter(Long userId);

    void sendEventToUser(Long userId, String eventType, Object payload);

    void sendEventToProvider(Long providerId, String eventType, Object payload);

    void broadcastEvent(String eventType, Object payload);
}
