package com.fptufa26.ecomotion.common.service.implement;

import tools.jackson.databind.ObjectMapper;
import com.fptufa26.ecomotion.common.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class NotificationStreamServiceImpl implements NotificationStreamService {

    private final Map<Long, Set<SseEmitter>> activeEmitters = new ConcurrentHashMap<>();
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
            activeEmitters.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
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
    public void removeEmitter(Long userId, SseEmitter emitter) {
        activeEmitters.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    @Override
    public void sendEventToUser(Long userId, String eventType, Object payload) {
        if (userId == null) return;
        // Notify clients only after the state is committed, never on rollback.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverEvent(userId, eventType, payload);
                }
            });
            return;
        }
        deliverEvent(userId, eventType, payload);
    }

    private void deliverEvent(Long userId, String eventType, Object payload) {
        Set<SseEmitter> emitters = activeEmitters.get(userId);
        if (emitters == null) return;
        String jsonPayload = (payload instanceof String value) ? value : objectMapper.writeValueAsString(payload);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(jsonPayload));
            } catch (IOException | IllegalStateException ex) {
                removeEmitter(userId, emitter);
                emitter.complete();
                log.debug("Closed SSE connection for user {}", userId);
            }
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
        activeEmitters.keySet().forEach(userId -> sendEventToUser(userId, eventType, payload));
    }
}
