package com.fptufa26.ecomotion.schedule.dto.request;

import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateBlockScheduleRequest(
        @NotNull(message = "Phương tiện không được để trống")
        Long vehicleId,

        @NotNull(message = "Loại lịch chặn không được để trống")
        ScheduleType type,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        Instant startAt,

        @NotNull(message = "Thời gian kết thúc không được để trống")
        Instant endAt,

        @Size(max = 255, message = "Lý do chặn không được vượt quá 255 ký tự")
        String reason
) {
}
