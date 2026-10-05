package com.fptufa26.ecomotion.booking.service.impl;

import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import com.fptufa26.ecomotion.booking.entity.ChargeType;
import com.fptufa26.ecomotion.booking.mapper.BookingMapper;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<BookingStatus> BLOCKING = EnumSet.of(
            BookingStatus.PENDING,
            BookingStatus.ACCEPTED,
            BookingStatus.CONFIRMED,
            BookingStatus.IN_PROGRESS);

    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final BookingChargeRepository chargeRepository;
    private final BookingMapper bookingMapper;
    private final UserService userService;
    private final VehicleCatalogService vehicleCatalogService;
    private final ScheduleService scheduleService;

    @Override
    @Transactional
    public CreateBookingResponse create(Long userId, CreateBookingRequest request) {
        Renter renter = userService.requireRenterByUserId(userId);
        Vehicle vehicle = vehicleCatalogService.requireApproved(request.vehicleId());
        RentalPlan plan = vehicleCatalogService.requireActivePlan(request.vehicleId(), request.planId());

        if (request.passengerCount() > vehicle.getSeatCount()) {
            throw new ConflictException("Passenger count exceeds seat count");
        }

        Instant start = request.pickupTime();
        Instant end = start.plus(Duration.ofMinutes((long) plan.getSlotLengthMinutes() * request.slotCount()));
        if (!end.isAfter(start)) {
            throw new ConflictException("Rental range is empty");
        }
        if (!scheduleService.isRangeFree(vehicle.getId(), start, end)) {
            throw new ConflictException("Vehicle is not free in the selected range");
        }
        if (renterOverlaps(renter.getId(), start, end)) {
            throw new ConflictException("Renter already has a booking in the selected range");
        }

        BigDecimal total = plan.getSlotPrice().multiply(BigDecimal.valueOf(request.slotCount()));
        User actor = renter.getUser();

        Booking booking = new Booking();
        booking.setBookingCode(nextBookingCode());
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(plan);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickupAddress(request.pickupAddress().trim());
        booking.setPickupTime(start);
        booking.setPassengerCount(request.passengerCount());
        booking.setTotalAmount(total);
        bookingRepository.save(booking);

        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setSlotPrice(plan.getSlotPrice());
        snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
        snapshot.setPlanType(plan.getPlanType());
        snapshot.setDepositRatio(plan.getPlanType() == PlanType.YEARLY ? null : vehicle.getProvider().getDepositRatio());
        snapshot.setUpfrontAmount(plan.getUpfrontAmount());
        snapshot.setAllowedOvertimeMinutes(plan.getAllowedOvertimeMinutes());
        snapshot.setOvertimeSurchargeRatio(plan.getOvertimeSurchargeRatio());
        snapshot.setWaitingFreeMinutes(plan.getWaitingFreeMinutes());
        snapshot.setWaitingRate(plan.getWaitingRate());
        snapshot.setWeekendSurchargeRatio(plan.getWeekendSurchargeRatio());
        snapshot.setFreeRadiusKm(plan.getFreeRadiusKm());
        snapshot.setOutOfRadiusRate(plan.getOutOfRadiusRate());
        snapshot.setHourlyToDailyThresholdMinutes(plan.getHourlyToDailyThresholdMinutes());
        snapshotRepository.save(snapshot);

        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(ChargeType.BASE_RENTAL);
        charge.setDescription("Thuê " + request.slotCount() + " slot");
        charge.setQuantity(request.slotCount());
        charge.setUnitPrice(plan.getSlotPrice());
        charge.setAmount(total);
        charge.setCreatedBy(actor);
        charge.setWaived(false);
        chargeRepository.save(charge);

        return bookingMapper.toCreateBookingResponse(booking, end, charge);
    }

    private boolean renterOverlaps(Long renterId, Instant start, Instant end) {
        List<Booking> existing = bookingRepository.findByRenter_IdAndStatusIn(renterId, BLOCKING);
        for (Booking booking : existing) {
            Instant otherEnd = endOf(booking);
            if (start.isBefore(otherEnd) && booking.getPickupTime().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private Instant endOf(Booking booking) {
        BookingPolicySnapshot snapshot = snapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        if (snapshot == null) {
            return booking.getPickupTime();
        }
        int slots = chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        return booking.getPickupTime().plus(Duration.ofMinutes((long) snapshot.getSlotLengthMinutes() * slots));
    }

    private String nextBookingCode() {
        int year = Year.now(ZONE).getValue();
        String prefix = "EM-" + year + "-";
        long sequence = bookingRepository.countByBookingCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }
}
