package com.fptufa26.ecomotion.verification.identity.dto.response;

import java.time.Instant;

public record IdentityReviewEventResponse(Long id, String eventType, String actorType, Long actorId,
        String reasonCode, String note, String policyVersion, int documentRevision, long attemptVersion,
        Instant occurredAt, IdentityAiAssessmentResponse.DocumentRead document, IdentityVideoResult video) {}
