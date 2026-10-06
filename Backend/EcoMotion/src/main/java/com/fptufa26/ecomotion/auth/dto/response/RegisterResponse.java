package com.fptufa26.ecomotion.auth.dto.response;

/**
 * Result of self-registration. The account is always created in {@code PENDING} status — it can
 * only become {@code ACTIVE} once the activation page completes the phone verification and the
 * CCCD check. {@code activationToken} authorises those activation calls (no session is issued yet).
 */
public record RegisterResponse(
        boolean created,
        String message,
        String activationToken,
        long activationTokenExpiresInSeconds
) {
}
