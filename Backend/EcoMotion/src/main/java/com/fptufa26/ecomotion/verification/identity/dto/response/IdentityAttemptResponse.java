package com.fptufa26.ecomotion.verification.identity.dto.response;

import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record IdentityAttemptResponse(
        UUID attemptId,
        Long version,
        Role role,
        IdentityAttemptStatus status,
        int documentRevision,
        String processingStage,
        String nextAction,
        String lastErrorCode,
        Instant expiresAt,
        boolean hasFront,
        boolean hasBack,
        boolean hasVideo,
        IdentityAiAssessmentResponse.DocumentRead document,
        IdentityVideoResult video,
        String identityDecision,
        boolean canSubmit,
        boolean automaticApproval,
        Long userId, String email, String fullName,
        String decisionActor, String decisionReason, String policyVersion,
        Instant submittedAt, Instant decidedAt, Long reviewedBy, Long resultingIdentityId,
        java.util.List<IdentityReviewEventResponse> reviewHistory,
        java.util.List<com.fptufa26.ecomotion.verification.progress.VerificationPipelineRunService.View> pipelineReports) {
    public IdentityAttemptResponse(
        UUID attemptId,
        Long version,
        Role role,
        IdentityAttemptStatus status,
        int documentRevision,
        String processingStage,
        String nextAction,
        String lastErrorCode,
        Instant expiresAt,
        boolean hasFront,
        boolean hasBack,
        boolean hasVideo,
        IdentityAiAssessmentResponse.DocumentRead document,
        IdentityVideoResult video,
        String identityDecision,
        boolean canSubmit,
        boolean automaticApproval,
        Long userId, String email, String fullName,
        String decisionActor, String decisionReason, String policyVersion,
        Instant submittedAt, Instant decidedAt, Long reviewedBy, Long resultingIdentityId,
        java.util.List<IdentityReviewEventResponse> reviewHistory) { this(attemptId, version, role, status, documentRevision, processingStage, nextAction, lastErrorCode, expiresAt, hasFront, hasBack, hasVideo, document, video, identityDecision, canSubmit, automaticApproval, userId, email, fullName, decisionActor, decisionReason, policyVersion, submittedAt, decidedAt, reviewedBy, resultingIdentityId, reviewHistory, java.util.List.of()); }
}
