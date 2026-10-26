package com.fptufa26.ecomotion.common.cloudinary;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {

    CloudinaryUploadResult uploadTripEvidence(MultipartFile file, Long bookingId);

    void deleteImage(String publicId);
}
