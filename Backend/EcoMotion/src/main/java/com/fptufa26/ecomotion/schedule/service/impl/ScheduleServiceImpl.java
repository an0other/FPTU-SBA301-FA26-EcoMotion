package com.fptufa26.ecomotion.schedule.service.impl;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
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

    @Override
    @Transactional
    public void book(Vehicle vehicle, Booking booking, Instant start, Instant end) {
        VehicleSchedule row = new VehicleSchedule();
        row.setVehicle(vehicle);
        row.setBooking(booking);
        row.setType(ScheduleType.BOOKED);
        row.setStartAt(start);
        row.setEndAt(end);
        row.setExtension(false);
        row.setStatus(ScheduleStatus.ACTIVE);
        vehicleScheduleRepository.save(row);
    }

    @Override
    @Transactional
    public void release(Long bookingId) {
        for (VehicleSchedule row : vehicleScheduleRepository.findByBooking_Id(bookingId)) {
            if (row.getStatus() == ScheduleStatus.ACTIVE) {
                row.setStatus(ScheduleStatus.CANCELLED);
            }
        }
    }
}
