package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.time.Instant;

public interface ScheduleService {

    boolean isRangeFree(Long vehicleId, Instant start, Instant end);

    void book(Vehicle vehicle, Booking booking, Instant start, Instant end);

    void release(Long bookingId);
}
