package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripEvidenceRequest(
        @NotNull(message = "Loại bằng chứng là bắt buộc")
        TripEvidenceType evidenceType,

        @NotBlank(message = "Đường dẫn hình ảnh là bắt buộc")
        @Size(max = 500, message = "Đường dẫn không vượt quá 500 ký tự")
        String imageUrl,

        @Size(max = 500, message = "Ghi chú không vượt quá 500 ký tự")
        String note
) {
}
