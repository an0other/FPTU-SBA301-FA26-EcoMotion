package com.fptufa26.ecomotion.schedule.dto.response;

import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;

import java.time.Instant;

public record ProviderScheduleItemResponse(
        Long id,
        Long vehicleId,
        String vehicleLicensePlate,
        String vehicleBrand,
        String vehicleModel,
        ScheduleType type,
        Instant startAt,
        Instant endAt,
        ScheduleStatus status,
        String reason,
        Long bookingId,
        String bookingCode,
        Instant createdAt
) {
}
