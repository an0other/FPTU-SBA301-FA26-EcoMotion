package com.fptufa26.ecomotion.verification.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

public record IdentityLinkResponse(UUID id, Long version, Long sourceUserId, String sourceEmail,
        Long targetUserId, String targetEmail, String status, Instant expiresAt, Instant confirmedAt,
        com.fptufa26.ecomotion.user.enums.UserStatus targetAccountStatus, String nextAction) { }
