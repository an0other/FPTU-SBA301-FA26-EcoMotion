package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityDecisionResponse;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAttemptResponse;
import java.util.List;
import java.util.UUID;

public interface IdentityDecisionService {
    String snapshotProviderEvidence(Long adminId, UUID attemptId);
    com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAttemptResponse reviewSnapshot(String snapshot);
    byte[] reviewSnapshotEvidence(String snapshot, String kind);
    boolean providerEvidenceReady(Long userId);
    UUID submitProviderEvidence(Long userId);
    void reviewProviderEvidence(Long adminId, Long userId, UUID attemptId, String decision, String reasonCode);
    IdentityDecisionResponse submit(Long userId, UUID attemptId, long version, boolean confirmed);
    IdentityDecisionResponse reviewRenter(Long adminId, UUID attemptId, long version, String decision, String reasonCode, String note);
    byte[] historyEvidence(Long adminId, UUID attemptId, Long eventId, String kind);
    IdentityDecisionResponse review(Long adminId, UUID attemptId, long version, String decision, String reasonCode);
    List<IdentityDecisionResponse> pending(Long adminId, int page,
            com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus status);
    IdentityAttemptResponse detail(Long adminId, UUID attemptId);
    byte[] evidence(Long adminId, UUID attemptId, String kind);
}
