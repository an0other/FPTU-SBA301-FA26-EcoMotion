package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.request.CreateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.request.ToggleDriverStatusRequest;
import com.fptufa26.ecomotion.user.dto.request.UpdateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverItemResponse;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverStatsResponse;
import com.fptufa26.ecomotion.user.enums.DriverStatus;

import java.util.List;

public interface ProviderDriverService {

    List<ProviderDriverItemResponse> getDrivers(Long providerUserId, String search, DriverStatus status, String sort);

    ProviderDriverStatsResponse getDriverStats(Long providerUserId);

    ProviderDriverItemResponse getDriverById(Long providerUserId, Long driverId);

    ProviderDriverItemResponse createDriver(Long providerUserId, CreateDriverByProviderRequest request);

    ProviderDriverItemResponse updateDriver(Long providerUserId, Long driverId, UpdateDriverByProviderRequest request);

    ProviderDriverItemResponse toggleDriverStatus(Long providerUserId, Long driverId, ToggleDriverStatusRequest request);
}
