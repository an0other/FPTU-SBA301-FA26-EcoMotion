package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.TripIncidentType;
import com.fptufa26.ecomotion.booking.enums.TripIncidentUrgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

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

        // Danh sách URL ảnh do upload minh chứng trả về. Chấp nhận cả một chuỗi (client cũ); việc chuẩn hóa,
        // giới hạn số lượng và https nằm ở IncidentEvidenceUrls.normalize.
        @com.fasterxml.jackson.annotation.JsonFormat(with = com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<String> evidenceUrls
) {
}
