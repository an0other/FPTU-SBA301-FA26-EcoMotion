package com.fptufa26.ecomotion.schedule.service.impl;

import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {

    private final VehicleScheduleRepository vehicleScheduleRepository;

    @Override
    public boolean isRangeFree(Long vehicleId, Instant start, Instant end) {
        return !vehicleScheduleRepository.existsActiveOverlap(vehicleId, start, end);
    }
}
