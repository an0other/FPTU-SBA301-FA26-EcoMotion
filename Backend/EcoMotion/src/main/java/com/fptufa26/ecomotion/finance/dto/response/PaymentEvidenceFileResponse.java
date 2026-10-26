package com.fptufa26.ecomotion.finance.dto.response;

public record PaymentEvidenceFileResponse(
        byte[] content,
        String contentType,
        String filename
) {
}
