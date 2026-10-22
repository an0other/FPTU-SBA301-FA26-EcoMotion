package com.fptufa26.ecomotion.user.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateDriverByProviderRequest(
        @NotBlank(message = "Họ và tên tài xế không được để trống")
        @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
        String fullName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @NotBlank(message = "Ảnh chân dung không được để trống")
        @Size(max = 500, message = "Đường dẫn ảnh chân dung tối đa 500 ký tự")
        String portraitUrl,

        @NotBlank(message = "Số GPLX không được để trống")
        @Size(max = 20, message = "Số GPLX tối đa 20 ký tự")
        String licenseNo,

        @NotBlank(message = "Hạng GPLX không được để trống")
        @Size(max = 10, message = "Hạng GPLX tối đa 10 ký tự")
        String licenseClass,

        @NotNull(message = "Ngày hết hạn GPLX không được để trống")
        @FutureOrPresent(message = "GPLX phải còn thời hạn sử dụng")
        LocalDate licenseExpiry,

        @NotBlank(message = "Ảnh GPLX không được để trống")
        @Size(max = 500, message = "Đường dẫn ảnh GPLX tối đa 500 ký tự")
        String licenseImageUrl
) {
}
