package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.response.DriverStatusResponse;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.enums.DriverOperationalStatus;

public interface DriverStatusService {

    DriverOperationalStatus calculateOperationalStatus(Driver driver);

    DriverStatusResponse getDriverStatus(Long currentUserId);

    DriverStatusResponse updateActivityStatus(Long currentUserId, DriverActivityStatus newStatus);

    void updateStatusTimestamp(Long driverId);
}
