package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.OperationalResolutionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ResolveOperationalRiskRequest(
        @NotNull OperationalResolutionType resolution,
        Long replacementVehicleId,
        Instant newPickupTime,
        @NotBlank @Size(max = 500) String reason
) {
}
