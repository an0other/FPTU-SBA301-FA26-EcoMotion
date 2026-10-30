package com.fptufa26.ecomotion.schedule.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.schedule.dto.response.OccupiedScheduleWindow;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
    public List<OccupiedScheduleWindow> occupiedWindows(Long vehicleId, Instant from, Instant to) {
        return vehicleScheduleRepository.findOccupiedInRange(vehicleId, from, to).stream()
                .map(schedule -> new OccupiedScheduleWindow(
                        schedule.getStartAt(), schedule.getEndAt(), schedule.getType()))
                .toList();
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
    public void hold(Vehicle vehicle, Booking booking, Instant start, Instant end, Instant expiresAt) {
        VehicleSchedule row = new VehicleSchedule();
        row.setVehicle(vehicle);
        row.setBooking(booking);
        row.setType(ScheduleType.BOOKED);
        row.setStartAt(start);
        row.setEndAt(end);
        row.setExtension(false);
        row.setStatus(ScheduleStatus.HELD);
        row.setExpiresAt(expiresAt);
        vehicleScheduleRepository.save(row);
    }

    @Override
    @Transactional
    public void renewHold(Long bookingId, Instant expiresAt) {
        VehicleSchedule row = vehicleScheduleRepository.findByBooking_Id(bookingId).stream()
                .filter(schedule -> schedule.getStatus() == ScheduleStatus.HELD)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Booking does not have an active vehicle hold"));
        row.setExpiresAt(expiresAt);
    }

    @Override
    @Transactional
    public void rescheduleHold(Booking booking, Instant start, Instant end) {
        Long bookingId = booking.getId();
        VehicleSchedule row = vehicleScheduleRepository.findByBooking_Id(bookingId).stream()
                .filter(schedule -> schedule.getStatus() == ScheduleStatus.HELD)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Booking does not have an active vehicle hold"));
        if (vehicleScheduleRepository.existsOccupiedOverlapExcludingBooking(
                booking.getVehicle().getId(), start, end, bookingId)) {
            throw new com.fptufa26.ecomotion.common.exception.ConflictException(
                    "Vehicle is not free in the selected range (including maintenance buffer)");
        }
        row.setStartAt(start);
        row.setEndAt(end);
    }

    @Override
    @Transactional
    public void moveBooking(Booking booking, Vehicle vehicle, Instant start, Instant end) {
        Long bookingId = booking.getId();
        VehicleSchedule row = vehicleScheduleRepository.findByBooking_Id(bookingId).stream()
                .filter(schedule -> schedule.getType() == ScheduleType.BOOKED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy lịch xe của booking"));
        if (vehicleScheduleRepository.existsOccupiedOverlapExcludingBooking(
                vehicle.getId(), start, end, bookingId)) {
            throw new com.fptufa26.ecomotion.common.exception.ConflictException(
                    "Xe thay thế không còn trống trong khoảng thời gian đã chọn");
        }
        row.setVehicle(vehicle);
        row.setStartAt(start);
        row.setEndAt(end);
    }

    @Override
    @Transactional
    public void reissueHold(Booking booking, Instant expiresAt) {
        Long bookingId = booking.getId();
        VehicleSchedule row = vehicleScheduleRepository.findByBooking_Id(bookingId).stream()
                .filter(schedule -> schedule.getType() == ScheduleType.BOOKED)
                .findFirst()
                .orElseGet(() -> newLegacyHold(booking));
        if (vehicleScheduleRepository.existsOccupiedOverlapExcludingBooking(
                row.getVehicle().getId(), row.getStartAt(), row.getEndAt(), bookingId)) {
            throw new com.fptufa26.ecomotion.common.exception.ConflictException(
                    "Lịch xe đã được đơn khác giữ; không thể cấp lại yêu cầu đặt cọc");
        }
        row.setStatus(ScheduleStatus.HELD);
        row.setExpiresAt(expiresAt);
    }

    private VehicleSchedule newLegacyHold(Booking booking) {
        if (booking.getPlan() == null || booking.getPlan().getSlotPrice() == null
                || booking.getPlan().getSlotPrice().signum() <= 0) {
            throw new IllegalStateException("Không thể xác định thời gian thuê của đơn cũ");
        }
        long slots = Math.max(1, booking.getTotalAmount()
                .divideToIntegralValue(booking.getPlan().getSlotPrice()).longValue());
        Instant endAt = booking.getPickupTime().plusSeconds(
                Math.multiplyExact(slots, booking.getPlan().getSlotLengthMinutes()) * 60L);
        VehicleSchedule row = new VehicleSchedule();
        row.setVehicle(booking.getVehicle());
        row.setBooking(booking);
        row.setType(ScheduleType.BOOKED);
        row.setStartAt(booking.getPickupTime());
        row.setEndAt(endAt);
        row.setExtension(false);
        return vehicleScheduleRepository.save(row);
    }

    @Override
    @Transactional
    public void confirmHold(Long bookingId) {
        VehicleSchedule row = vehicleScheduleRepository.findByBooking_Id(bookingId).stream()
                .filter(schedule -> schedule.getStatus() == ScheduleStatus.HELD)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Booking does not have an active vehicle hold"));
        row.setStatus(ScheduleStatus.ACTIVE);
        row.setExpiresAt(null);
    }

    @Override
    @Transactional
    public boolean confirmUnexpiredHold(Long bookingId, Instant now) {
        VehicleSchedule row = availableHold(bookingId, now);
        if (row == null) return false;
        row.setStatus(ScheduleStatus.ACTIVE);
        row.setExpiresAt(null);
        return true;
    }

    @Override
    public boolean hasUnexpiredHold(Long bookingId, Instant now) {
        return availableHold(bookingId, now) != null;
    }

    private VehicleSchedule availableHold(Long bookingId, Instant now) {
        VehicleSchedule row = vehicleScheduleRepository.findByBooking_Id(bookingId).stream()
                .filter(s -> s.getStatus() == ScheduleStatus.HELD && s.getExpiresAt() != null
                        && s.getExpiresAt().isAfter(now))
                .findFirst().orElse(null);
        if (row == null || vehicleScheduleRepository.existsOccupiedOverlapExcludingBooking(
                row.getVehicle().getId(), row.getStartAt(), row.getEndAt(), bookingId)) return null;
        return row;
    }

    @Override
    @Transactional
    public void release(Long bookingId) {
        for (VehicleSchedule row : vehicleScheduleRepository.findByBooking_Id(bookingId)) {
            if (row.getStatus() == ScheduleStatus.ACTIVE || row.getStatus() == ScheduleStatus.HELD) {
                row.setStatus(ScheduleStatus.CANCELLED);
            }
        }
    }

    @Override
    public List<Long> expiredHoldBookingIds(Instant now) {
        return vehicleScheduleRepository.findExpiredHoldBookingIds(now);
    }
    @Override
    public java.util.List<VehicleSchedule> findActiveInRange(Long vehicleId, Instant start, Instant end) {
        if (vehicleId == null || start == null || end == null) {
            return java.util.List.of();
        }
        return vehicleScheduleRepository.findActiveInRange(vehicleId, start, end);
    }
}
