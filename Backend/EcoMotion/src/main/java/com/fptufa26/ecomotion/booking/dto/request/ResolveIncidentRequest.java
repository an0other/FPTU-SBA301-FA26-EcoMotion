package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.TripIncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolveIncidentRequest(
        @NotBlank(message = "Ghi chú xử lý là bắt buộc")
        @Size(max = 1000, message = "Ghi chú không quá 1000 ký tự")
        String resolutionNote,

        @NotNull(message = "Trạng thái sau xử lý là bắt buộc")
        TripIncidentStatus status
) {
}
