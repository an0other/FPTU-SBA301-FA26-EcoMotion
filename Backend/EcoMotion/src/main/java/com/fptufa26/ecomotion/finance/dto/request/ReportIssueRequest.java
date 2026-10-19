package com.fptufa26.ecomotion.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportIssueRequest(
        @NotBlank(message = "Mô tả vấn đề/sai lệch không được để trống")
        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String issueNote
) {
}
