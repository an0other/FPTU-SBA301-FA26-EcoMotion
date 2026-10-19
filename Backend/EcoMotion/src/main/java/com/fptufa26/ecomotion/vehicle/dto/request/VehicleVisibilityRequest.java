package com.fptufa26.ecomotion.vehicle.dto.request;

import jakarta.validation.constraints.NotNull;

public record VehicleVisibilityRequest(
        @NotNull(message = "visible flag is required")
        Boolean visible
) {
}

