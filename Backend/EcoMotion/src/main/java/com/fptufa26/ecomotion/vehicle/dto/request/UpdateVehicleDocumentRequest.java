package com.fptufa26.ecomotion.vehicle.dto.request;

import com.fptufa26.ecomotion.vehicle.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateVehicleDocumentRequest(
        @NotNull(message = "Loại giấy tờ không được để trống")
        DocumentType docType,

        @NotBlank(message = "Số hiệu giấy tờ không được để trống")
        @Size(max = 50, message = "Số hiệu giấy tờ tối đa 50 ký tự")
        String docNumber,

        LocalDate expiryDate,

        @NotBlank(message = "Đường dẫn tệp đính kèm không được để trống")
        @Size(max = 500, message = "Đường dẫn tệp tối đa 500 ký tự")
        String fileUrl
) {
}
