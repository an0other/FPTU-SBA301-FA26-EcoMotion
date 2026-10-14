package com.fptufa26.ecomotion.auth.otp;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class OtpEntry {

    private final String code;
    private final String phone;
    private final Instant expiresAt;
    private final AtomicInteger attemptsUsed = new AtomicInteger(0);
    private volatile Instant lastSentAt;

    public OtpEntry(String code, String phone, Instant expiresAt, Instant lastSentAt) {
        this.code = code;
        this.phone = phone;
        this.expiresAt = expiresAt;
        this.lastSentAt = lastSentAt;
    }

    public String getCode() {
        return code;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(Instant lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public int incrementAttempts() {
        return attemptsUsed.incrementAndGet();
    }

    public int getAttemptsUsed() {
        return attemptsUsed.get();
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
