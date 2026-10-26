package com.fptufa26.ecomotion.schedule.dto.response;

import com.fptufa26.ecomotion.schedule.enums.ScheduleType;

import java.time.Instant;

public record OccupiedScheduleWindow(
        Instant startAt,
        Instant endAt,
        ScheduleType type
) {
}
