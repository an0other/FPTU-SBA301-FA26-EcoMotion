package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.request.CreateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.request.ToggleDriverStatusRequest;
import com.fptufa26.ecomotion.user.dto.request.UpdateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverItemResponse;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverStatsResponse;
import com.fptufa26.ecomotion.user.enums.DriverOperationalStatus;
import com.fptufa26.ecomotion.user.enums.DriverStatus;

import java.util.List;
import java.time.LocalDate;
import com.fptufa26.ecomotion.common.response.PageResponse;

public interface ProviderDriverService {

    default List<ProviderDriverItemResponse> getDrivers(Long providerUserId, String search, DriverStatus status, String sort) {
        return getDrivers(providerUserId, search, status, null, sort);
    }

    List<ProviderDriverItemResponse> getDrivers(Long providerUserId, String search, DriverStatus status, DriverOperationalStatus operationalStatus, String sort);

    PageResponse<ProviderDriverItemResponse> getDriversPaged(Long providerUserId, String search, DriverStatus status,
            DriverOperationalStatus operationalStatus, String sort, LocalDate updatedFrom, LocalDate updatedTo,
            int offset, int limit);

    ProviderDriverStatsResponse getDriverStats(Long providerUserId);

    ProviderDriverItemResponse getDriverById(Long providerUserId, Long driverId);

    ProviderDriverItemResponse createDriver(Long providerUserId, CreateDriverByProviderRequest request);

    ProviderDriverItemResponse updateDriver(Long providerUserId, Long driverId, UpdateDriverByProviderRequest request);

    ProviderDriverItemResponse toggleDriverStatus(Long providerUserId, Long driverId, ToggleDriverStatusRequest request);
}
