package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.DocumentStatus;
import com.fptufa26.ecomotion.vehicle.enums.DocumentType;
import java.time.Instant;
import java.time.LocalDate;

public record ProviderVehicleDocumentResponse(
        Long id,
        Long vehicleId,
        DocumentType docType,
        String docNumber,
        LocalDate expiryDate,
        String fileUrl,
        DocumentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
