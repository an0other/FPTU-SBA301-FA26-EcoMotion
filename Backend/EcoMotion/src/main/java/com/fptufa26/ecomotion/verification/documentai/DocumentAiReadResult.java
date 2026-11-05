package com.fptufa26.ecomotion.verification.documentai;

import java.time.LocalDate;

public record DocumentAiReadResult(
        String documentNumber,
        String fullName,
        LocalDate dateOfBirth,
        String licenseClass,
        LocalDate issueDate,
        LocalDate expiryDate,
        boolean expiryUnlimited,
        String requestId
) {
}
