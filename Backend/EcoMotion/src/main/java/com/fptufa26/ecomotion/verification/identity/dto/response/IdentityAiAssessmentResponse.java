package com.fptufa26.ecomotion.verification.identity.dto.response;

import java.util.List;
import java.util.UUID;

public record IdentityAiAssessmentResponse(
        UUID requestId,
        DocumentRead document,
        IdentityVideoResult video,
        String workflowStatus,
        List<String> reasons,
        String identityDecision,
        boolean automaticApproval,
        boolean assessmentOnly) {

    public record DocumentRead(
            String documentType, String status, String code,
            String documentNumber, String fullName, String dateOfBirth,
            String expiryDate, String sex, String address,
            boolean requiredFieldsReadable) {
    }
}
