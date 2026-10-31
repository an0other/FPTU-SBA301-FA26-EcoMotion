package com.fptufa26.ecomotion.otp.service.implement;

import com.fptufa26.ecomotion.otp.exception.OtpAttemptsExceededException;
import com.fptufa26.ecomotion.notification.external.dto.request.OtpNotificationRequest;
import com.fptufa26.ecomotion.notification.external.dto.response.NotificationResult;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.notification.external.exception.NotificationDeliveryException;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.dto.response.OtpChallenge;
import com.fptufa26.ecomotion.otp.dto.response.OtpVerificationResult;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.otp.exception.OtpExpiredException;
import com.fptufa26.ecomotion.otp.exception.OtpInvalidException;
import com.fptufa26.ecomotion.otp.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.otp.model.OtpEntry;
import com.fptufa26.ecomotion.otp.service.OtpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

@Service
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<StoreKey, OtpEntry> store = new ConcurrentHashMap<>();

    private final ExternalNotificationDispatcherService externalNotificationDispatcherService;
    private final int codeLength;
    private final Duration ttl;
    private final int maxAttempts;
    private final Duration resendCooldown;
    public OtpServiceImpl(
            ExternalNotificationDispatcherService externalNotificationDispatcherService,
            @Value("${app.otp.length:6}") int codeLength,
            @Value("${app.otp.ttl:5m}") Duration ttl,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.resend-cooldown:60s}") Duration resendCooldown) {
        this.externalNotificationDispatcherService = externalNotificationDispatcherService;
        this.codeLength = codeLength;
        this.ttl = ttl;
        this.maxAttempts = maxAttempts;
        this.resendCooldown = resendCooldown;
    }

    @Override
    public OtpChallenge send(OtpSendRequest request) {
        requireValid(request);
        StoreKey storeKey = new StoreKey(normalizeKey(request.key()), request.purpose());
        String recipient = normalizeRecipient(request.recipient(), request.channel());
        Instant now = Instant.now();
        evictExpired(now);
        requireCooldownOver(storeKey, now, resendCooldown);

        String code = generateCode(codeLength);
        OtpEntry entry = new OtpEntry(code, recipient, now.plus(ttl), now);

        NotificationResult result = externalNotificationDispatcherService.sendOtp(new OtpNotificationRequest(
                recipient, code, ttl.toSeconds(), request.channel()));
        if (!result.success()) {
            throw new NotificationDeliveryException("Could not deliver OTP to " + recipient);
        }

        store.put(storeKey, entry);
        return new OtpChallenge(code, ttl.toSeconds(), resendCooldown.toSeconds());
    }

    @Override
    public OtpVerificationResult verify(OtpVerifyRequest request) {
        requireValid(request);
        StoreKey storeKey = new StoreKey(normalizeKey(request.key()), request.purpose());
        Instant now = Instant.now();
        evictExpired(now);

        OtpEntry entry = store.get(storeKey);
        if (entry == null || entry.isExpired(now)) {
            throw new OtpExpiredException();
        }
        if (entry.getAttemptsUsed() >= maxAttempts) {
            throw new OtpAttemptsExceededException();
        }
        if (!entry.getCode().equals(request.code().trim())) {
            int used = entry.incrementAttempts();
            if (used >= maxAttempts) {
                store.remove(storeKey);
                throw new OtpAttemptsExceededException();
            }
            throw new OtpInvalidException();
        }

        store.remove(storeKey);
        return new OtpVerificationResult(entry.getRecipient());
    }

    private void requireCooldownOver(StoreKey key, Instant now, Duration cooldown) {
        OtpEntry existing = store.get(key);
        if (existing != null && !existing.isExpired(now)
                && now.isBefore(existing.getLastSentAt().plus(cooldown))) {
            throw new OtpResendTooSoonException();
        }
    }

    private String generateCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    private void evictExpired(Instant now) {
        store.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    private void requireValid(OtpSendRequest request) {
        if (request == null || isBlank(request.key()) || isBlank(request.recipient())
                || request.purpose() == null || request.channel() == null) {
            throw new IllegalArgumentException("OTP key, recipient, purpose and channel are required");
        }
    }

    private void requireValid(OtpVerifyRequest request) {
        if (request == null || isBlank(request.key()) || isBlank(request.code()) || request.purpose() == null) {
            throw new IllegalArgumentException("OTP key, code and purpose are required");
        }
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRecipient(String recipient, NotificationChannel channel) {
        String normalized = recipient.trim();
        return channel == NotificationChannel.EMAIL
                ? normalized.toLowerCase(Locale.ROOT)
                : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StoreKey(String key, OtpPurpose purpose) {
    }

}
