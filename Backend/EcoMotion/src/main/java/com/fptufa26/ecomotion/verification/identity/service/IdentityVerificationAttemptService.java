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
}
