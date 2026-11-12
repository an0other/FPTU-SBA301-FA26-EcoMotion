package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityConstraintsResponse;
import com.fptufa26.ecomotion.verification.identity.integration.IdentityVerificationClient;
import com.fptufa26.ecomotion.verification.identity.repository.IdentityVerificationAttemptRepository;
import com.fptufa26.ecomotion.verification.identity.service.IdentityReadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;

@Service
public class IdentityReadServiceImpl implements IdentityReadService {
    private final UserService users;
    private final IdentityVerificationAttemptRepository attempts;
    private final IdentityVerificationClient ai;
    private final int maxImageBytes;
    private final int maxVideoBytes;
    private final long maxImagePixels;
    private final com.fptufa26.ecomotion.verification.identity.service.IdentityVerificationAttemptService sessions;
    @Value("${app.identity.ai.enabled:false}") private boolean enabled;
    @Value("${app.identity.acceptance-threshold:}") private String threshold;

    public IdentityReadServiceImpl(UserService users, IdentityVerificationAttemptRepository attempts,
            IdentityVerificationClient ai, @Value("${app.identity.ai.max-image-bytes:8388608}") int imageBytes,
            @Value("${app.identity.ai.max-video-bytes:20971520}") int videoBytes,
            @Value("${app.identity.ai.max-image-pixels:20000000}") long imagePixels,
            com.fptufa26.ecomotion.verification.identity.service.IdentityVerificationAttemptService sessions) {
        this.users = users;
        this.attempts = attempts;
        this.ai = ai;
        maxImageBytes = imageBytes;
        maxVideoBytes = videoBytes;
        maxImagePixels = imagePixels;
        this.sessions = sessions;
    }

    @Override
    public IdentityConstraintsResponse constraints() {
        var result = ai.constraints();
        return new IdentityConstraintsResponse(Math.min(maxImageBytes, result.maxImageBytes()),
                result.maxImagePixels() == null ? maxImagePixels : Math.min(maxImagePixels, result.maxImagePixels()),
                Math.min(maxVideoBytes, result.maxVideoBytes()), result.maxVideoSeconds(), result.maxVideoFps(),
                result.maxVideoFrames(), result.maxVideoPixels(), result.imageFormats(), result.videoContainers(),
                result.challenge(), result.minimumHoldSeconds(), enabled, threshold != null && !threshold.isBlank());
    }

    @Override
    @Transactional
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityStatusResponse status(Long userId) {
        var user = users.findById(userId).orElseThrow(() -> new AccessDeniedException("Account not found"));
        if (user.getStatus() == UserStatus.LOCKED || !Set.of(Role.RENTER, Role.PROVIDER).contains(user.getRole())) {
            throw new AccessDeniedException("Account unavailable");
        }
        var latest = attempts.findFirstByUser_IdOrderByIdDesc(userId).orElse(null);
        var attempt = latest == null ? null : sessions.get(userId, latest.getPublicId());
        boolean verified = user.getIdentity() != null && user.getIdentity().getStatus()
                == com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED;
        boolean reviewed = user.getIdentityReviewedAt() != null;
        boolean restrictedIdentity = user.getIdentity() != null && (!verified
                || user.getIdentity().getDocumentExpiry() != null
                && user.getIdentity().getDocumentExpiry().isBefore(java.time.LocalDate.now(java.time.ZoneOffset.UTC)));
        String next = restrictedIdentity ? "CONTACT_SUPPORT" : user.getPhoneVerifiedAt() == null ? "VERIFY_PHONE"
                : verified && (user.getRole() == Role.RENTER || reviewed)
                ? user.getRole() == Role.RENTER ? "LOGIN_OR_DASHBOARD" : "PROVIDER_APPLICATION"
                : attempt == null ? "START_VERIFICATION" : attempt.nextAction();
        return new com.fptufa26.ecomotion.verification.identity.dto.response.IdentityStatusResponse(userId,
                user.getRole(), user.getStatus(), user.getPhoneVerifiedAt() != null,
                user.getIdentity() == null ? null : user.getIdentity().getId(),
                user.getIdentity() == null ? "UNVERIFIED" : user.getIdentity().getStatus().name(), reviewed,
                attempt == null ? null : attempt.attemptId(), attempt == null ? null : attempt.status(),
                attempt == null ? null : attempt.version(), attempt == null ? null : attempt.decisionReason(),
                false,
                !restrictedIdentity && (user.getIdentity() == null || user.getRole() == Role.PROVIDER && !reviewed), next);
    }
}
