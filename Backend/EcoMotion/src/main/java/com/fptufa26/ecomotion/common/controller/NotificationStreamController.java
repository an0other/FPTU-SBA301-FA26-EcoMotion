package com.fptufa26.ecomotion.common.controller;

import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.common.service.NotificationStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Notification Stream", description = "Server-Sent Events for real-time notification sync")
public class NotificationStreamController {

    private static final Long SSE_TIMEOUT = 120_000L; // 2 minutes timeout with keep-alive
    private final NotificationStreamService notificationStreamService;

    @GetMapping(value = {"/api/v1/notifications/stream", "/api/notifications/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live Server-Sent Events stream for authenticated user")
    public SseEmitter streamNotifications(@AuthenticationPrincipal SecurityUser principal) {
        Long userId = (principal != null) ? principal.getUserId() : 0L;
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        notificationStreamService.registerEmitter(userId, emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE stream completed for user {}", userId);
            notificationStreamService.removeEmitter(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE stream timed out for user {}", userId);
            emitter.complete();
            notificationStreamService.removeEmitter(userId, emitter);
        });

        emitter.onError((ex) -> {
            log.debug("SSE stream error for user {}: {}", userId, ex.getMessage());
            emitter.complete();
            notificationStreamService.removeEmitter(userId, emitter);
        });

        try {
            // Initial connect handshake event
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("{\"status\":\"connected\",\"userId\":" + userId + "}")
            );
        } catch (IOException ex) {
            log.warn("Failed to send initial SSE handshake to user {}: {}", userId, ex.getMessage());
            emitter.complete();
            notificationStreamService.removeEmitter(userId, emitter);
        }

        return emitter;
    }
}
