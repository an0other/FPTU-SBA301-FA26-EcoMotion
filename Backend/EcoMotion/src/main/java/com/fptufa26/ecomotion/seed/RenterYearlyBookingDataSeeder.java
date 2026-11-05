package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class RenterYearlyBookingDataSeeder {

    private static final String BOOKING_CODE = "EM-SEED-R14-001";
    private static final String RENTER_EMAIL = "renter.demo@ecomotion.vn";
    private static final int YEAR_SLOT_MINUTES = 525600;
    private static final int RENTAL_YEARS = 3;
    private static final int PICKUP_LEAD_DAYS = 30;
    private static final BigDecimal DEMO_YEARLY_SLOT_PRICE =
            new BigDecimal("180000000.00");
    private static final BigDecimal DEMO_UPFRONT_AMOUNT =
            new BigDecimal("50000000.00");

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final EntityManager entityManager;

    @Transactional
    public void seed() {
        if (bookingRepository.existsByBookingCode(BOOKING_CODE)) {
            return;
        }

        User renterUser = userRepository.findByEmailIgnoreCase(RENTER_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "R14 seed renter user is missing"));

        Renter renter = renterRepository.findByUser_Id(renterUser.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "R14 seed renter profile is missing"));

        Vehicle vehicle = vehicleRepository.findAll().stream()
                .filter(candidate ->
                        candidate.getStatus() == VehicleStatus.APPROVED)
                .filter(candidate ->
                        candidate.getProvider().getStatus()
                                == ProviderStatus.APPROVED)
                .sorted(Comparator.comparing(Vehicle::getId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "R14 seed requires an approved vehicle and provider"));

        RentalPlan plan = findOrCreateYearlyPlan(vehicle);

        if (plan.getSlotPrice() == null
                || plan.getSlotPrice().signum() <= 0
                || plan.getUpfrontAmount() == null
                || plan.getUpfrontAmount().signum() <= 0) {
            throw new IllegalStateException(
                    "R14 seed requires valid yearly price and upfront amount");
        }

        Instant requestedAt = Instant.now();
        Instant pickupTime = requestedAt
                .plus(PICKUP_LEAD_DAYS, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.HOURS);

        Booking booking = new Booking();
        booking.setBookingCode(BOOKING_CODE);
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(plan);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickupAddress(
                vehicle.getProvider().getDepotAddress());
        booking.setPickupTime(pickupTime);
        booking.setPassengerCount(1);
        booking.setTotalAmount(
                plan.getSlotPrice().multiply(
                        BigDecimal.valueOf(RENTAL_YEARS)));
        booking.setCreatedAt(requestedAt);
        booking.setUpdatedAt(requestedAt);
        entityManager.persist(booking);

        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setPlanType(PlanType.YEARLY);
        snapshot.setSlotPrice(plan.getSlotPrice());
        snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
        snapshot.setUpfrontAmount(plan.getUpfrontAmount());
        snapshot.setCreatedAt(requestedAt);
        entityManager.persist(snapshot);

        booking.setStatus(BookingStatus.ACCEPTED);
    }

    private RentalPlan findOrCreateYearlyPlan(Vehicle vehicle) {
        RentalPlan existing = rentalPlanRepository
                .findByVehicle_IdOrderByIdAsc(vehicle.getId())
                .stream()
                .filter(plan -> plan.getPlanType() == PlanType.YEARLY)
                .filter(plan -> plan.getStatus() == PlanStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        RentalPlan plan = new RentalPlan();
        plan.setVehicle(vehicle);
        plan.setPlanType(PlanType.YEARLY);
        plan.setSlotLengthMinutes(YEAR_SLOT_MINUTES);
        plan.setSlotPrice(DEMO_YEARLY_SLOT_PRICE);
        plan.setUpfrontAmount(DEMO_UPFRONT_AMOUNT);
        plan.setStatus(PlanStatus.ACTIVE);

        return rentalPlanRepository.saveAndFlush(plan);
    }
}