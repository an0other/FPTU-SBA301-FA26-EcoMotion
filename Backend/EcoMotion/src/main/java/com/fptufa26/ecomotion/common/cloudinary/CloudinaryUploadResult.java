package com.fptufa26.ecomotion.common.cloudinary;

public record CloudinaryUploadResult(
        String secureUrl,
        String publicId,
        String format,
        Long bytes,
        Integer width,
        Integer height
) {
}
