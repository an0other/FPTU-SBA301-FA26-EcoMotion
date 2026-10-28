package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.time.Instant;
import java.util.List;
import com.fptufa26.ecomotion.schedule.dto.response.OccupiedScheduleWindow;

public interface ScheduleService {

    boolean isRangeFree(Long vehicleId, Instant start, Instant end);

    List<OccupiedScheduleWindow> occupiedWindows(Long vehicleId, Instant from, Instant to);

    void book(Vehicle vehicle, Booking booking, Instant start, Instant end);

    void hold(Vehicle vehicle, Booking booking, Instant start, Instant end, Instant expiresAt);

    void renewHold(Long bookingId, Instant expiresAt);

    void rescheduleHold(Booking booking, Instant start, Instant end);

    void moveBooking(Booking booking, Vehicle vehicle, Instant start, Instant end);

    void reissueHold(Booking booking, Instant expiresAt);

    void confirmHold(Long bookingId);

    void release(Long bookingId);

    List<VehicleSchedule> findActiveInRange(Long vehicleId, Instant start, Instant end);
    List<Long> expiredHoldBookingIds(Instant now);
}
