package com.fptufa26.ecomotion.auth.service;

/**
 * In-memory OTP challenges, one per channel:
 * <ul>
 *     <li><b>email</b> — keyed by address, delivered through {@code OtpSender};</li>
 *     <li><b>phone</b> — keyed by user id, <b>mocked</b>: no SMS gateway is wired up, so the code is
 *     returned to the caller (and shown in the UI) to keep the activation wizard testable.</li>
 * </ul>
 */
public interface OtpService {

    /**
     * Generates an email OTP, delivers it, and returns the challenge metadata. Throws
     * {@code OtpResendTooSoonException} if called again within the cooldown window.
     */
    OtpChallenge send(String email);

    /**
     * Verifies a submitted email code. Throws {@code OtpExpiredException},
     * {@code OtpInvalidException} or {@code OtpAttemptsExceededException}. A successful
     * verification consumes the entry.
     */
    void verify(String email, String code);

    /**
     * Generates a mocked phone OTP for the given key (the pending user id). Throws
     * {@code OtpResendTooSoonException} when called again within the cooldown window.
     */
    PhoneOtpChallenge sendPhone(String key, String phone);

    /** Verifies the phone code and returns the phone number the challenge was bound to. */
    String verifyPhone(String key, String code);

    record OtpChallenge(String email, String code, long expiresInSeconds, long resendCooldownSeconds) {
    }

    record PhoneOtpChallenge(String code, long expiresInSeconds, long resendCooldownSeconds) {
    }
}
