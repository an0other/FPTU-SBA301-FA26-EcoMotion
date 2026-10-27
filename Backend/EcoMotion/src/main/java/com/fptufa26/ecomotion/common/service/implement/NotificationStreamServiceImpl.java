package com.fptufa26.ecomotion.common.service.implement;

import tools.jackson.databind.ObjectMapper;
import com.fptufa26.ecomotion.common.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class NotificationStreamServiceImpl implements NotificationStreamService {

    private final Map<Long, SseEmitter> activeEmitters = new ConcurrentHashMap<>();
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotificationStreamServiceImpl(UserService userService, @Autowired(required = false) ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void registerEmitter(Long userId, SseEmitter emitter) {
        if (userId != null && emitter != null) {
            activeEmitters.put(userId, emitter);
            log.debug("Registered SSE emitter for userId: {}", userId);
        }
    }

    @Override
    public void removeEmitter(Long userId) {
        if (userId != null) {
            activeEmitters.remove(userId);
            log.debug("Removed SSE emitter for userId: {}", userId);
        }
    }

    @Override
    public void sendEventToUser(Long userId, String eventType, Object payload) {
        if (userId == null) return;
        SseEmitter emitter = activeEmitters.get(userId);
        if (emitter == null) {
            log.debug("No active SSE emitter for userId: {}", userId);
            return;
        }

        try {
            String jsonPayload = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(jsonPayload)
            );
            log.debug("Sent SSE event '{}' to userId {}", eventType, userId);
        } catch (IOException ex) {
            log.warn("Failed to send SSE event to userId {}: {}", userId, ex.getMessage());
            emitter.complete();
            activeEmitters.remove(userId);
        }
    }

    @Override
    public void sendEventToProvider(Long providerId, String eventType, Object payload) {
        if (providerId == null) return;
        userService.findProviderById(providerId).ifPresent(provider -> {
            if (provider.getUser() != null && provider.getUser().getId() != null) {
                sendEventToUser(provider.getUser().getId(), eventType, payload);
            }
        });
    }

    @Override
    public void broadcastEvent(String eventType, Object payload) {
        if (activeEmitters.isEmpty()) return;
        try {
            String jsonPayload = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
            activeEmitters.forEach((userId, emitter) -> {
                try {
                    emitter.send(SseEmitter.event().name(eventType).data(jsonPayload));
                } catch (IOException ex) {
                    emitter.complete();
                    activeEmitters.remove(userId);
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to broadcast SSE event {}: {}", eventType, ex.getMessage());
        }
    }
}
