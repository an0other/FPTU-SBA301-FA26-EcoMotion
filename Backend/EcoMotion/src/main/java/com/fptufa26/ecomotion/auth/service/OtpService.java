package com.fptufa26.ecomotion.auth.service;

public interface OtpService {

    OtpChallenge send(String email);

    void verify(String email, String code);

    PhoneOtpChallenge sendPhone(String key, String phone);

    String verifyPhone(String key, String code);

    record OtpChallenge(String email, String code, long expiresInSeconds, long resendCooldownSeconds) {
    }

    record PhoneOtpChallenge(String code, long expiresInSeconds, long resendCooldownSeconds) {
    }
}
