package com.fptufa26.ecomotion.user.dto.response;

import org.springframework.http.MediaType;

public record DrivingLicenseImage(byte[] content, MediaType mediaType) {
}
