package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.TripIncidentType;
import com.fptufa26.ecomotion.booking.enums.TripIncidentUrgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentReportRequest(
        @NotNull(message = "Loại sự cố là bắt buộc")
        TripIncidentType incidentType,

        @NotBlank(message = "Mô tả sự cố là bắt buộc")
        @Size(max = 1000, message = "Mô tả không quá 1000 ký tự")
        String description,

        @NotNull(message = "Mức độ khẩn cấp là bắt buộc")
        TripIncidentUrgency urgency,

        Double latitude,

        Double longitude,

        @Size(max = 2000, message = "Đường dẫn minh chứng không quá 2000 ký tự")
        String evidenceUrls
) {
}
