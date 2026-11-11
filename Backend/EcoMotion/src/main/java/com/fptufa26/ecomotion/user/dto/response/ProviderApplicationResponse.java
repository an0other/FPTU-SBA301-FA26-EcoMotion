package com.fptufa26.ecomotion.user.dto.response;

import com.fptufa26.ecomotion.user.dto.request.ProviderApplicationRequest;
import com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus;
import java.time.Instant;

public record ProviderApplicationResponse(Long userId, Long version, ProviderApplicationStatus status,
        ProviderApplicationRequest profile, Instant submittedAt, Instant reviewedAt, Long reviewedBy,
        String reasonCode, boolean identityVerified, boolean canEdit, boolean canSubmit, String nextAction,
        String email, String fullName, java.util.UUID identityAttemptId, String reviewNote) {
}
