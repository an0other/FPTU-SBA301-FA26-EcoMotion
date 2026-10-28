package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportDriverIssueRequest(
        @NotBlank(message = "Lý do báo sự cố là bắt buộc")
        @Size(max = 500, message = "Lý do báo sự cố không được vượt quá 500 ký tự")
        String reason
) {
}
