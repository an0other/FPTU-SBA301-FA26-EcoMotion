package com.fptufa26.ecomotion.verification.identity.dto.response;

import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus;
import java.util.UUID;

public record IdentityStatusResponse(Long userId, Role role, UserStatus accountStatus, boolean phoneVerified,
        Long identityId, String identityStatus, boolean providerIdentityReviewed, UUID attemptId,
        IdentityAttemptStatus attemptStatus, Long attemptVersion, String reasonCode,
        boolean canInitiateLink, boolean canStartVerification, String nextAction) { }
