package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.booking.dto.response.AssignmentAvailabilityResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DriverAvailabilityService {

    Map<Long, AssignmentAvailabilityResponse> inspect(
            Long providerId, List<Driver> drivers, Vehicle vehicle, Instant start, Instant end, Long excludeBookingId);

    List<Driver> findAvailable(Long providerId, Vehicle vehicle, Instant start, Instant end,
                               Long excludeBookingId);

    boolean isAvailable(Long driverId, Vehicle vehicle, Instant start, Instant end,
                        Long excludeBookingId);
}
