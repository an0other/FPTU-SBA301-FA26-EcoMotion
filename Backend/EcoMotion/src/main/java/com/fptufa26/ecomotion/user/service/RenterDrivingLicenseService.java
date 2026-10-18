package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.request.UpdateDrivingLicenseRequest;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseResponse;

public interface RenterDrivingLicenseService {

    DrivingLicenseResponse getDrivingLicense(Long userId);

    DrivingLicenseResponse updateDrivingLicense(
            Long userId,
            UpdateDrivingLicenseRequest request);
}