package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.request.UpdateDrivingLicenseRequest;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseResponse;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseClassOptionResponse;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseImage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface RenterDrivingLicenseService {

    DrivingLicenseResponse getDrivingLicense(Long userId);

    DrivingLicenseResponse updateDrivingLicense(
            Long userId,
            UpdateDrivingLicenseRequest request);

    DrivingLicenseResponse uploadDrivingLicense(
            Long userId,
            MultipartFile front,
            MultipartFile back);

    List<DrivingLicenseClassOptionResponse> getSupportedLicenseClasses();

    Optional<DrivingLicenseImage> getDrivingLicenseImage(Long userId, String side);
}