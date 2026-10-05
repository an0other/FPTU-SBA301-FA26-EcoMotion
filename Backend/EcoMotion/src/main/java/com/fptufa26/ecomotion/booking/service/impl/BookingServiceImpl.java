package com.fptufa26.ecomotion.booking.service.impl;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import com.fptufa26.ecomotion.booking.entity.ChargeType;
import com.fptufa26.ecomotion.booking.entity.PaymentDirection;
import com.fptufa26.ecomotion.booking.entity.PaymentStatus;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.entity.PaymentType;
import com.fptufa26.ecomotion.booking.mapper.BookingMapper;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
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
import java.math.RoundingMode;
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
    private final PaymentTransactionRepository paymentTransactionRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> listMine(Long userId, BookingStatus status) {
        Renter renter = userService.requireRenterByUserId(userId);
        List<Booking> bookings = status == null
                ? bookingRepository.findByRenter_IdOrderByPickupTimeDesc(renter.getId())
                : bookingRepository.findByRenter_IdAndStatusOrderByPickupTimeDesc(renter.getId(), status);
        return bookings.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse previewCheckout(Long userId, String bookingCode) {
        Booking booking = requireAccepted(userId, bookingCode);
        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        int slots = baseSlots(booking);
        Instant end = endOf(booking, snapshot, slots);
        BigDecimal due = amountDue(booking, snapshot);
        String imageUrl = vehicleCatalogService.coverImageUrl(booking.getVehicle().getId());
        return bookingMapper.toCheckoutPreview(booking, snapshot, imageUrl, end, slots, due);
    }

    @Override
    @Transactional
    public CheckoutResultResponse checkout(Long userId, String bookingCode, CheckoutRequest request) {
        Booking booking = requireAccepted(userId, bookingCode);
        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        int slots = baseSlots(booking);
        Instant end = endOf(booking, snapshot, slots);
        if (!scheduleService.isRangeFree(booking.getVehicle().getId(), booking.getPickupTime(), end)) {
            throw new ConflictException("Vehicle is not free in the selected range");
        }

        BigDecimal due = amountDue(booking, snapshot);
        if (due == null || due.signum() <= 0) {
            throw new ConflictException("Amount due must be positive");
        }

        User actor = booking.getRenter().getUser();
        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setAmount(due);
        payment.setDirection(PaymentDirection.IN);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());

        if (snapshot.getPlanType() == PlanType.YEARLY) {
            payment.setType(PaymentType.UPFRONT_PAY);
        } else {
            BookingCharge deposit = new BookingCharge();
            deposit.setBooking(booking);
            deposit.setChargeType(ChargeType.DEPOSIT);
            deposit.setDescription("Cọc giữ chỗ");
            deposit.setQuantity(null);
            deposit.setUnitPrice(booking.getTotalAmount());
            deposit.setAppliedRate(snapshot.getDepositRatio());
            deposit.setAmount(due);
            deposit.setCreatedBy(actor);
            deposit.setWaived(false);
            chargeRepository.save(deposit);
            payment.setType(PaymentType.DEPOSIT_PAY);
            payment.setCharge(deposit);
        }
        paymentTransactionRepository.save(payment);

        scheduleService.book(booking.getVehicle(), booking, booking.getPickupTime(), end);
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingMapper.toCheckoutResult(booking, payment, end);
    }

    private BookingSummaryResponse toSummary(Booking booking) {
        PlanType planType = snapshotRepository.findByBooking_Id(booking.getId())
                .map(BookingPolicySnapshot::getPlanType)
                .orElseGet(() -> booking.getPlan().getPlanType());
        String imageUrl = vehicleCatalogService.coverImageUrl(booking.getVehicle().getId());
        return bookingMapper.toSummary(booking, planType, imageUrl);
    }

    private Booking requireAccepted(Long userId, String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        Long ownerId = booking.getRenter().getUser().getUserId();
        if (!ownerId.equals(userId)) {
            throw new NotFoundException("Booking not found");
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new ConflictException("Booking is not accepted");
        }
        return booking;
    }

    private BookingPolicySnapshot requireSnapshot(Booking booking) {
        return snapshotRepository.findByBooking_Id(booking.getId())
                .orElseThrow(() -> new NotFoundException("Booking snapshot not found"));
    }

    private int baseSlots(Booking booking) {
        return chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElseThrow(() -> new NotFoundException("Base rental charge not found"));
    }

    private Instant endOf(Booking booking, BookingPolicySnapshot snapshot, int slots) {
        return booking.getPickupTime().plus(Duration.ofMinutes((long) snapshot.getSlotLengthMinutes() * slots));
    }

    private BigDecimal amountDue(Booking booking, BookingPolicySnapshot snapshot) {
        if (snapshot.getPlanType() == PlanType.YEARLY) {
            return snapshot.getUpfrontAmount();
        }
        return booking.getTotalAmount()
                .multiply(snapshot.getDepositRatio())
                .setScale(2, RoundingMode.HALF_UP);
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
