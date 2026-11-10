package com.fptufa26.ecomotion.vehicle.dto.request;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateVehicleRequest(
        @NotBlank(message = "Vui lòng nhập biển số xe kiểm soát") @Pattern(regexp = "^[0-9]{2}[a-zA-Z]{1,2}[- .]?[0-9]{3}[- .]?[0-9]{1,2}$", message = "Biển số xe không đúng định dạng quy chuẩn Việt Nam (VD: 51K-334.12 hoặc 51A-11111)") String licensePlate,
        @NotBlank(message = "Vui lòng nhập hãng xe") @Size(max = 50, message = "Hãng xe không được vượt quá 50 ký tự") String brand,
        @NotBlank(message = "Vui lòng nhập dòng xe") @Size(max = 50, message = "Dòng xe không được vượt quá 50 ký tự") String model,
        @NotNull(message = "Vui lòng chọn năm sản xuất") @Min(value = 2015, message = "Năm sản xuất tối thiểu là 2015") @Max(value = 2026, message = "Năm sản xuất không hợp lệ") Integer manufactureYear,
        @NotNull(message = "Vui lòng chọn số chỗ ngồi") @Min(value = 1, message = "Số chỗ ngồi tối thiểu là 1") Integer seatCount,
        @NotNull(message = "Vui lòng chọn loại hộp số") Transmission transmission,
        @NotNull(message = "Vui lòng chọn loại nhiên liệu") FuelType fuelType,
        @NotBlank(message = "Vui lòng nhập màu xe") @Size(max = 40, message = "Màu xe không được vượt quá 40 ký tự") String color,
        @NotNull(message = "Vui lòng thiết lập khoảng đệm bảo trì") @Min(value = 0, message = "Khoảng đệm bảo trì không được âm") Integer maintenanceBufferMinutes,
        @Size(max = 500, message = "Đường dẫn ảnh bìa không được vượt quá 500 ký tự") String coverImageUrl,
        List<@Size(max = 500, message = "Đường dẫn ảnh chi tiết không được vượt quá 500 ký tự") String> galleryImageUrls
) {

    public UpdateVehicleRequest(
            String licensePlate,
            String brand,
            String model,
            Integer manufactureYear,
            Integer seatCount,
            Transmission transmission,
            FuelType fuelType,
            String color,
            Integer maintenanceBufferMinutes,
            String coverImageUrl
    ) {
        this(licensePlate, brand, model, manufactureYear, seatCount, transmission, fuelType, color, maintenanceBufferMinutes, coverImageUrl, List.of());
    }
}

