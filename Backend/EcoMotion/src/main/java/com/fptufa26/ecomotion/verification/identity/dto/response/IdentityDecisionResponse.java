package com.fptufa26.ecomotion.verification.identity.dto.response;

import com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus;
import java.time.Instant;
import java.util.UUID;

public record IdentityDecisionResponse(UUID attemptId, Long version, IdentityAttemptStatus status,
        String actor, String reasonCode, String policyVersion, Instant submittedAt, Instant decidedAt,
        Long resultingIdentityId, boolean accountActivated, Long userId, String email, String fullName) {
}
