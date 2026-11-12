package com.fptufa26.ecomotion.verification.identity.dto.response;

import com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus;
import java.time.Instant;
import java.util.UUID;

/** Actual AI stage when available; upload percentage is a separate browser measurement. */
public record IdentityProcessingResponse(UUID attemptId, IdentityAttemptStatus status,
        String processingStage, Instant startedAt, Instant observedAt, Instant deadlineAt,
        String errorCode, Long version, int documentRevision,
        com.fptufa26.ecomotion.verification.progress.AiProgressContext.Snapshot aiProgress) {}
