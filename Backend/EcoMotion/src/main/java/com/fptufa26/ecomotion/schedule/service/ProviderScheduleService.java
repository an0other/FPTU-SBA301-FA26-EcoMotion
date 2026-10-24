package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleItemResponse;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleStatsResponse;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;

import java.time.Instant;
import java.util.List;

public interface ProviderScheduleService {

    long countActiveBlocksByProviderId(Long providerId);

    long countCurrentlyBlockedVehiclesByProviderId(Long providerId);

    ProviderScheduleStatsResponse getScheduleStats(Long userId);

    List<ProviderScheduleItemResponse> getProviderSchedules(
            Long userId,
            Long vehicleId,
            ScheduleType type,
            ScheduleStatus status,
            Instant fromDate,
            Instant toDate
    );

    ProviderScheduleItemResponse createBlockSchedule(Long userId, CreateBlockScheduleRequest request);

    ProviderScheduleItemResponse cancelBlockSchedule(Long userId, Long scheduleId);
}
