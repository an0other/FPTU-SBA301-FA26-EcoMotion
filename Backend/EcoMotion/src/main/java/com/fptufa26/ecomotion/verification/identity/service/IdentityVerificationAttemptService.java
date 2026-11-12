package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAttemptResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IdentityVerificationAttemptService {

    IdentityAttemptResponse createOrResume(Long userId);

    IdentityAttemptResponse current(Long userId);

    IdentityAttemptResponse get(Long userId, UUID attemptId);

    IdentityAttemptResponse uploadDocuments(
            Long userId, UUID attemptId, long version, MultipartFile front, MultipartFile back);

    IdentityAttemptResponse uploadVideo(Long userId, UUID attemptId, long version, MultipartFile video);

    IdentityAttemptResponse cancel(Long userId, UUID attemptId, long version);

    byte[] evidence(Long userId, UUID attemptId, String kind);
    byte[] storedEvidence(String key, String kind);
    com.fptufa26.ecomotion.verification.identity.dto.response.IdentityProcessingResponse progress(Long userId, UUID attemptId);
    java.util.List<IdentityAttemptResponse> history(Long userId, int page);
    byte[] historyEvidence(Long userId, UUID attemptId, Long eventId, String kind);
}
