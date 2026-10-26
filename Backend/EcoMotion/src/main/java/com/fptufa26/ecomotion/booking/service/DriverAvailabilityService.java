package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.time.Instant;
import java.util.List;

public interface DriverAvailabilityService {

    List<Driver> findAvailable(Long providerId, Vehicle vehicle, Instant start, Instant end,
                               Long excludeBookingId);

    boolean isAvailable(Long driverId, Vehicle vehicle, Instant start, Instant end,
                        Long excludeBookingId);
}
