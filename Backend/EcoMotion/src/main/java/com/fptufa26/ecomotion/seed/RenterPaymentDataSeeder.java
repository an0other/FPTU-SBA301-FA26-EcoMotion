package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class RenterPaymentDataSeeder {

    private static final String R12_BOOKING_CODE = "EM-SEED-R12-001";
    private static final String R12_RENTER_EMAIL = "renter.demo@ecomotion.vn";
    private static final String R12_GATEWAY_REF = "SEED-R12-DEPOSIT-001";
    private static final int DAILY_SLOT_MINUTES = 1440;
    private static final int BOOKING_LEAD_DAYS = 7;

    private final BookingRepository bookingRepository;
    private final VehicleScheduleRepository vehicleScheduleRepository;
    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final EntityManager entityManager;

    @Transactional
    public void seed() {
        if (bookingRepository.existsByBookingCode(R12_BOOKING_CODE)) {
            return;
        }

        User renterUser = userRepository.findByEmailIgnoreCase(R12_RENTER_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "R12 seed renter user is missing"));

        Renter renter = renterRepository.findByUser_Id(renterUser.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "R12 seed renter profile is missing"));

        RentalPlan plan = findDailyPlan();
        Vehicle vehicle = plan.getVehicle();
        Provider provider = vehicle.getProvider();

        Instant createdAt = Instant.now();
        Instant startAt = createdAt.plus(BOOKING_LEAD_DAYS, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.HOURS);
        Instant endAt = startAt.plus(
                plan.getSlotLengthMinutes(),
                ChronoUnit.MINUTES);

        validateAvailability(renter, vehicle, startAt, endAt);

        Booking booking = createBooking(
                renter,
                plan,
                provider,
                startAt,
                createdAt);
        entityManager.persist(booking);

        BookingPolicySnapshot snapshot = createSnapshot(
                booking,
                plan,
                provider,
                createdAt);
        entityManager.persist(snapshot);

        BigDecimal baseAmount = snapshot.getSlotPrice();
        BigDecimal depositAmount = baseAmount
                .multiply(snapshot.getDepositRatio())
                .setScale(2, RoundingMode.HALF_UP);

        if (depositAmount.signum() <= 0) {
            throw new IllegalStateException(
                    "R12 seed deposit must be positive");
        }

        BookingCharge baseCharge = createBaseCharge(
                booking,
                snapshot,
                provider.getUser(),
                createdAt);
        entityManager.persist(baseCharge);

        BookingCharge depositCharge = createDepositCharge(
                booking,
                snapshot,
                depositAmount,
                provider.getUser(),
                createdAt);
        entityManager.persist(depositCharge);

        booking.setStatus(BookingStatus.ACCEPTED);

        PaymentTransaction payment = createDepositPayment(
                booking,
                depositCharge,
                depositAmount,
                createdAt);
        entityManager.persist(payment);

        booking.setStatus(BookingStatus.CONFIRMED);

        VehicleSchedule schedule = createSchedule(
                booking,
                vehicle,
                startAt,
                endAt,
                createdAt);
        entityManager.persist(schedule);
    }

    private RentalPlan findDailyPlan() {
        return vehicleRepository.findAll().stream()
                .filter(vehicle ->
                        vehicle.getStatus() == VehicleStatus.APPROVED)
                .filter(vehicle ->
                        vehicle.getProvider().getStatus()
                                == ProviderStatus.APPROVED)
                .flatMap(vehicle -> rentalPlanRepository
                        .findByVehicle_IdOrderByIdAsc(vehicle.getId())
                        .stream())
                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE)
                .filter(plan -> plan.getPlanType() == PlanType.DAILY)
                .filter(plan -> Integer.valueOf(DAILY_SLOT_MINUTES)
                        .equals(plan.getSlotLengthMinutes()))
                .filter(plan -> plan.getSlotPrice() != null
                        && plan.getSlotPrice().signum() > 0)
                .filter(plan -> {
                    BigDecimal depositRatio =
                            plan.getVehicle().getProvider().getDepositRatio();

                    return depositRatio != null
                            && depositRatio.signum() > 0
                            && depositRatio.compareTo(BigDecimal.ONE) <= 0;
                })
                .sorted(Comparator.comparing(RentalPlan::getId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "R12 seed requires an approved vehicle with an active daily plan"));
    }

    private void validateAvailability(
            Renter renter,
            Vehicle vehicle,
            Instant startAt,
            Instant endAt) {
        Instant bufferedStart = startAt.minus(
                vehicle.getMaintenanceBufferMinutes(),
                ChronoUnit.MINUTES);
        Instant bufferedEnd = endAt.plus(
                vehicle.getMaintenanceBufferMinutes(),
                ChronoUnit.MINUTES);

        boolean vehicleBusy = vehicleScheduleRepository
                .existsByVehicle_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                        vehicle.getId(),
                        ScheduleStatus.ACTIVE,
                        bufferedEnd,
                        bufferedStart);

        boolean renterBusy = vehicleScheduleRepository
                .existsByBooking_Renter_IdAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                        renter.getId(),
                        ScheduleStatus.ACTIVE,
                        endAt,
                        startAt);

        if (vehicleBusy || renterBusy) {
            throw new IllegalStateException(
                    "R12 seed interval overlaps an existing vehicle or renter schedule");
        }
    }

    private Booking createBooking(
            Renter renter,
            RentalPlan plan,
            Provider provider,
            Instant startAt,
            Instant createdAt) {
        Booking booking = new Booking();
        booking.setBookingCode(R12_BOOKING_CODE);
        booking.setRenter(renter);
        booking.setVehicle(plan.getVehicle());
        booking.setPlan(plan);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickupAddress(provider.getDepotAddress());
        booking.setPickupTime(startAt);
        booking.setPassengerCount(1);
        booking.setTotalAmount(plan.getSlotPrice());
        booking.setCreatedAt(createdAt);
        return booking;
    }

    private BookingPolicySnapshot createSnapshot(
            Booking booking,
            RentalPlan plan,
            Provider provider,
            Instant createdAt) {
        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setSlotPrice(plan.getSlotPrice());
        snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
        snapshot.setPlanType(plan.getPlanType());
        snapshot.setDepositRatio(provider.getDepositRatio());
        snapshot.setAllowedOvertimeMinutes(plan.getAllowedOvertimeMinutes());
        snapshot.setOvertimeSurchargeRatio(plan.getOvertimeSurchargeRatio());
        snapshot.setWaitingFreeMinutes(plan.getWaitingFreeMinutes());
        snapshot.setWaitingRate(plan.getWaitingRate());
        snapshot.setWeekendSurchargeRatio(plan.getWeekendSurchargeRatio());
        snapshot.setFreeRadiusKm(plan.getFreeRadiusKm());
        snapshot.setOutOfRadiusRate(plan.getOutOfRadiusRate());
        snapshot.setHourlyToDailyThresholdMinutes(
                plan.getHourlyToDailyThresholdMinutes());
        snapshot.setCreatedAt(createdAt);
        return snapshot;
    }

    private BookingCharge createBaseCharge(
            Booking booking,
            BookingPolicySnapshot snapshot,
            User createdBy,
            Instant createdAt) {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(ChargeType.BASE_RENTAL);
        charge.setDescription("Daily rental: one slot");
        charge.setQuantity(1);
        charge.setUnitPrice(snapshot.getSlotPrice());
        charge.setAmount(snapshot.getSlotPrice());
        charge.setCreatedBy(createdBy);
        charge.setWaived(false);
        charge.setCreatedAt(createdAt);
        return charge;
    }

    private BookingCharge createDepositCharge(
            Booking booking,
            BookingPolicySnapshot snapshot,
            BigDecimal depositAmount,
            User createdBy,
            Instant createdAt) {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(ChargeType.DEPOSIT);
        charge.setDescription(
                "Booking deposit credited toward rental payment");
        charge.setUnitPrice(snapshot.getSlotPrice());
        charge.setAppliedRate(snapshot.getDepositRatio());
        charge.setAmount(depositAmount);
        charge.setCreatedBy(createdBy);
        charge.setWaived(false);
        charge.setCreatedAt(createdAt);
        return charge;
    }

    private PaymentTransaction createDepositPayment(
            Booking booking,
            BookingCharge depositCharge,
            BigDecimal depositAmount,
            Instant createdAt) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setCharge(depositCharge);
        payment.setType(PaymentType.DEPOSIT_PAY);
        payment.setAmount(depositAmount);
        payment.setDirection(PaymentDirection.IN);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PAID);
        payment.setGatewayRef(R12_GATEWAY_REF);
        payment.setPaidAt(createdAt);
        payment.setCreatedAt(createdAt);
        return payment;
    }

    private VehicleSchedule createSchedule(
            Booking booking,
            Vehicle vehicle,
            Instant startAt,
            Instant endAt,
            Instant createdAt) {
        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle);
        schedule.setBooking(booking);
        schedule.setType(ScheduleType.BOOKED);
        schedule.setStartAt(startAt);
        schedule.setEndAt(endAt);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setExtension(false);
        schedule.setCreatedAt(createdAt);
        return schedule;
    }
}