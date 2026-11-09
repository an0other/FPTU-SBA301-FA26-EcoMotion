package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityDecisionResponse;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAttemptResponse;
import java.util.List;
import java.util.UUID;

public interface IdentityDecisionService {
    boolean providerEvidenceReady(Long userId);
    UUID submitProviderEvidence(Long userId);
    void reviewProviderEvidence(Long adminId, Long userId, UUID attemptId, String decision, String reasonCode);
    IdentityDecisionResponse submit(Long userId, UUID attemptId, long version, boolean confirmed);
    IdentityDecisionResponse review(Long adminId, UUID attemptId, long version, String decision, String reasonCode);
    List<IdentityDecisionResponse> pending(Long adminId, int page,
            com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus status);
    IdentityAttemptResponse detail(Long adminId, UUID attemptId);
    byte[] evidence(Long adminId, UUID attemptId, String kind);
}
