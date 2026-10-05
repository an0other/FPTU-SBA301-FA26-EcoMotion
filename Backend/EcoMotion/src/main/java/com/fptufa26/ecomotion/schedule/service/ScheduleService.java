package com.fptufa26.ecomotion.schedule.service;

import java.time.Instant;

public interface ScheduleService {

    boolean isRangeFree(Long vehicleId, Instant start, Instant end);
}
