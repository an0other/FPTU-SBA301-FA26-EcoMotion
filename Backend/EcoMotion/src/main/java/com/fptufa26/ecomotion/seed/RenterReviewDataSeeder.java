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
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class RenterReviewDataSeeder {

    private static final String BOOKING_CODE = "EM-SEED-R16-001";
    private static final String SOURCE_BOOKING_CODE = "EM-SEED-R12-001";
    private static final String RENTER_EMAIL = "renter.demo@ecomotion.vn";
    private static final String DRIVER_EMAIL = "driver.review@ecomotion.vn";
    private static final String DRIVER_PHONE = "0901000099";
    private static final String DRIVER_LICENSE = "SEED-R16-D001";
    private static final int DAILY_SLOT_MINUTES = 1440;
    private static final int LOOKBACK_DAYS = 14;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final DriverRepository driverRepository;
    private final VehicleScheduleRepository vehicleScheduleRepository;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional
    public void seed() {
        if (bookingRepository.existsByBookingCode(BOOKING_CODE)) {
            return;
        }

        User renterUser = userRepository.findByEmailIgnoreCase(RENTER_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "R16 seed renter user is missing"));

        Renter renter = renterRepository.findByUser_Id(renterUser.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "R16 seed renter profile is missing"));

        Booking source = bookingRepository
                .findByBookingCodeAndRenter_User_Id(
                        SOURCE_BOOKING_CODE,
                        renterUser.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Run R12 seed before R16 seed"));

        Vehicle vehicle = source.getVehicle();
        RentalPlan plan = source.getPlan();
        Provider provider = vehicle.getProvider();

        validatePlan(vehicle, plan, provider);

        Instant startAt = Instant.now()
                .minus(LOOKBACK_DAYS, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.DAYS);
        Instant endAt = startAt.plus(
                plan.getSlotLengthMinutes(),
                ChronoUnit.MINUTES);
        Instant requestedAt = startAt.minus(2, ChronoUnit.DAYS);
        Instant depositPaidAt = startAt.minus(1, ChronoUnit.DAYS);
        Instant completedAt = endAt.plus(10, ChronoUnit.MINUTES);

        validateAvailability(renter, vehicle, startAt, endAt);

        Driver driver = findOrCreateDriver(provider);

        Booking booking = new Booking();
        booking.setBookingCode(BOOKING_CODE);
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(plan);
        booking.setDriver(driver);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickupAddress(provider.getDepotAddress());
        booking.setPickupTime(startAt);
        booking.setPassengerCount(1);
        booking.setTotalAmount(plan.getSlotPrice());
        booking.setCreatedAt(requestedAt);
        booking.setUpdatedAt(requestedAt);
        entityManager.persist(booking);

        BookingPolicySnapshot snapshot = createSnapshot(
                booking,
                plan,
                provider,
                requestedAt);
        entityManager.persist(snapshot);

        BigDecimal baseAmount = snapshot.getSlotPrice();
        BigDecimal depositAmount = baseAmount
                .multiply(snapshot.getDepositRatio())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = baseAmount.subtract(depositAmount);

        if (depositAmount.signum() <= 0) {
            throw new IllegalStateException(
                    "R16 seed requires a positive deposit");
        }

        BookingCharge baseCharge = createCharge(
                booking,
                provider.getUser(),
                ChargeType.BASE_RENTAL,
                1,
                baseAmount,
                null,
                baseAmount,
                requestedAt);
        entityManager.persist(baseCharge);

        BookingCharge depositCharge = createCharge(
                booking,
                provider.getUser(),
                ChargeType.DEPOSIT,
                null,
                baseAmount,
                snapshot.getDepositRatio(),
                depositAmount,
                requestedAt);
        entityManager.persist(depositCharge);

        persistPayment(
                booking,
                depositCharge,
                PaymentType.DEPOSIT_PAY,
                depositAmount,
                depositPaidAt,
                "SEED-R16-DEPOSIT-001");

        if (remainingAmount.signum() > 0) {
            persistPayment(
                    booking,
                    baseCharge,
                    PaymentType.SETTLEMENT,
                    remainingAmount,
                    completedAt,
                    "SEED-R16-SETTLEMENT-001");
        }

        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle);
        schedule.setBooking(booking);
        schedule.setType(ScheduleType.BOOKED);
        schedule.setStartAt(startAt);
        schedule.setEndAt(endAt);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setExtension(false);
        schedule.setCreatedAt(depositPaidAt);
        schedule.setUpdatedAt(depositPaidAt);
        entityManager.persist(schedule);

        booking.setActualStartAt(startAt);
        booking.setActualEndAt(endAt);
        booking.setRenterConfirmedAt(endAt.plus(5, ChronoUnit.MINUTES));
        booking.setProviderConfirmedAt(completedAt);
        booking.setStatus(BookingStatus.COMPLETED);
    }

    private void validatePlan(
            Vehicle vehicle,
            RentalPlan plan,
            Provider provider) {
        BigDecimal depositRatio = provider.getDepositRatio();

        if (vehicle.getStatus() != VehicleStatus.APPROVED
                || provider.getStatus() != ProviderStatus.APPROVED
                || plan.getStatus() != PlanStatus.ACTIVE
                || plan.getPlanType() != PlanType.DAILY
                || !Integer.valueOf(DAILY_SLOT_MINUTES)
                .equals(plan.getSlotLengthMinutes())
                || !plan.getVehicle().getId().equals(vehicle.getId())
                || plan.getSlotPrice() == null
                || plan.getSlotPrice().signum() <= 0
                || depositRatio == null
                || depositRatio.signum() <= 0
                || depositRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException(
                    "R16 seed requires an approved vehicle and a valid daily plan");
        }
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
                    "R16 seed interval overlaps an existing schedule");
        }
    }

    private Driver findOrCreateDriver(Provider provider) {
        User user = userRepository.findByEmailIgnoreCase(DRIVER_EMAIL)
                .orElseGet(() -> userService.createUser(
                        DRIVER_EMAIL,
                        DevDataSeeder.DEFAULT_PASSWORD,
                        "Review Demo Driver",
                        DRIVER_PHONE,
                        Role.DRIVER,
                        UserStatus.ACTIVE));

        if (user.getRole() != Role.DRIVER || !user.isActive()) {
            throw new IllegalStateException(
                    "R16 seed driver account has an unexpected role or status");
        }

        Driver existing = driverRepository.findByUser_Id(user.getId())
                .orElse(null);

        if (existing != null) {
            if (!existing.getProvider().getId().equals(provider.getId())
                    || existing.getStatus() != DriverStatus.APPROVED
                    || existing.getLicenseExpiry().isBefore(LocalDate.now())) {
                throw new IllegalStateException(
                        "R16 seed driver profile is incompatible");
            }
            return existing;
        }

        Driver driver = new Driver();
        driver.setUser(user);
        driver.setProvider(provider);
        driver.setLicenseNo(DRIVER_LICENSE);
        driver.setLicenseClass("B");
        driver.setLicenseExpiry(LocalDate.now().plusYears(3));
        driver.setPortraitUrl("/seed/r16/driver-portrait.png");
        driver.setLicenseImageUrl("/seed/r16/driver-license.png");
        driver.setStatus(DriverStatus.APPROVED);
        entityManager.persist(driver);
        return driver;
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

    private BookingCharge createCharge(
            Booking booking,
            User createdBy,
            ChargeType type,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal appliedRate,
            BigDecimal amount,
            Instant createdAt) {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(type);
        charge.setDescription(type == ChargeType.BASE_RENTAL
                ? "Daily rental: one slot"
                : "Booking deposit credited toward rental payment");
        charge.setQuantity(quantity);
        charge.setUnitPrice(unitPrice);
        charge.setAppliedRate(appliedRate);
        charge.setAmount(amount);
        charge.setCreatedBy(createdBy);
        charge.setCreatedAt(createdAt);
        return charge;
    }

    private void persistPayment(
            Booking booking,
            BookingCharge charge,
            PaymentType type,
            BigDecimal amount,
            Instant paidAt,
            String gatewayRef) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setCharge(charge);
        payment.setType(type);
        payment.setAmount(amount);
        payment.setDirection(PaymentDirection.IN);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PAID);
        payment.setGatewayRef(gatewayRef);
        payment.setPaidAt(paidAt);
        payment.setCreatedAt(paidAt);
        payment.setUpdatedAt(paidAt);
        entityManager.persist(payment);
    }

}