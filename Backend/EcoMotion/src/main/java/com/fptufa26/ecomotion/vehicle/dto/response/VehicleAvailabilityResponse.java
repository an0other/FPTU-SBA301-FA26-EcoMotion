package com.fptufa26.ecomotion.vehicle.dto.response;

import java.time.Instant;
import java.util.List;

public record VehicleAvailabilityResponse(
        Long vehicleId,
        List<OccupiedSlot> occupiedSlots
) {

    public record OccupiedSlot(
            Instant startAt,
            Instant endAt,
            String type
    ) {}
}
