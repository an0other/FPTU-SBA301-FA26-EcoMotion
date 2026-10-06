package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.exception.OtpAttemptsExceededException;
import com.fptufa26.ecomotion.auth.exception.OtpExpiredException;
import com.fptufa26.ecomotion.auth.exception.OtpInvalidException;
import com.fptufa26.ecomotion.auth.exception.OtpResendTooSoonException;
import com.fptufa26.ecomotion.auth.otp.OtpEntry;
import com.fptufa26.ecomotion.auth.otp.OtpSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory OTP store shared by the two channels: the registration email OTP (keyed by address,
 * delivered through {@link OtpSender}) and the activation phone OTP (keyed by user id, mocked —
 * the code is handed back to the caller so the wizard works without an SMS gateway).
 *
 * <p>Deliberately NOT persisted (spec requirement) and therefore lost on restart; acceptable for a
 * single-instance dev/prototype, not shared across replicas.</p>
 */
@Service
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    private final OtpSender otpSender;
    private final int codeLength;
    private final Duration ttl;
    private final int maxAttempts;
    private final Duration resendCooldown;
    private final int phoneCodeLength;
    private final Duration phoneTtl;
    private final int phoneMaxAttempts;
    private final Duration phoneResendCooldown;

    public OtpServiceImpl(
            OtpSender otpSender,
            @Value("${app.otp.length:6}") int codeLength,
            @Value("${app.otp.ttl:5m}") Duration ttl,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.resend-cooldown:60s}") Duration resendCooldown,
            @Value("${app.phone-otp.length:6}") int phoneCodeLength,
            @Value("${app.phone-otp.ttl:5m}") Duration phoneTtl,
            @Value("${app.phone-otp.max-attempts:5}") int phoneMaxAttempts,
            @Value("${app.phone-otp.resend-cooldown:60s}") Duration phoneResendCooldown) {
        this.otpSender = otpSender;
        this.codeLength = codeLength;
        this.ttl = ttl;
        this.maxAttempts = maxAttempts;
        this.resendCooldown = resendCooldown;
        this.phoneCodeLength = phoneCodeLength;
        this.phoneTtl = phoneTtl;
        this.phoneMaxAttempts = phoneMaxAttempts;
        this.phoneResendCooldown = phoneResendCooldown;
    }

    @Override
    public OtpChallenge send(String email) {
        String key = normalize(email);
        Instant now = Instant.now();
        evictExpired(now);
        requireCooldownOver(key, now, resendCooldown);

        String code = generateCode(codeLength);
        OtpEntry entry = new OtpEntry(code, null, now.plus(ttl), now);

        // Deliver BEFORE storing: if the address is rejected, nothing is left behind, so the
        // client cannot proceed to verify-otp and no user row can ever be created.
        otpSender.send(key, null, code);

        store.put(key, entry);
        return new OtpChallenge(key, code, ttl.toSeconds(), resendCooldown.toSeconds());
    }

    @Override
    public void verify(String email, String code) {
        verifyEntry(normalize(email), code, maxAttempts, false);
    }

    @Override
    public PhoneOtpChallenge sendPhone(String key, String phone) {
        Instant now = Instant.now();
        evictExpired(now);
        requireCooldownOver(key, now, phoneResendCooldown);

        String code = generateCode(phoneCodeLength);
        store.put(key, new OtpEntry(code, phone, now.plus(phoneTtl), now));
        return new PhoneOtpChallenge(code, phoneTtl.toSeconds(), phoneResendCooldown.toSeconds());
    }

    @Override
    public String verifyPhone(String key, String code) {
        return (String) verifyEntry(key, code, phoneMaxAttempts, true);
    }

    /**
     * Shared verification. Returns the verified phone number for the phone channel, or {@code null}
     * for the email channel. Throws on expired / too many attempts / mismatch; a successful
     * verification consumes the entry.
     */
    private Object verifyEntry(String key, String code, int attemptLimit, boolean phoneChannel) {
        Instant now = Instant.now();
        evictExpired(now);

        OtpEntry entry = store.get(key);
        if (entry == null || entry.isExpired(now)) {
            throw new OtpExpiredException();
        }
        if (entry.getAttemptsUsed() >= attemptLimit) {
            throw new OtpAttemptsExceededException();
        }
        if (code == null || !entry.getCode().equals(code.trim())) {
            int used = entry.incrementAttempts();
            if (used >= attemptLimit) {
                store.remove(key);
                throw new OtpAttemptsExceededException();
            }
            throw new OtpInvalidException();
        }

        store.remove(key);
        return phoneChannel ? entry.getPhone() : null;
    }

    private void requireCooldownOver(String key, Instant now, Duration cooldown) {
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

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
