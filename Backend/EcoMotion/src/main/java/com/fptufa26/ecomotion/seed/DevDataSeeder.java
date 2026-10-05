package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import com.fptufa26.ecomotion.booking.entity.ChargeType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.user.entity.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.ProviderStatus;
import com.fptufa26.ecomotion.user.entity.ProviderType;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.FuelType;
import com.fptufa26.ecomotion.vehicle.entity.PlanStatus;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Transmission;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.entity.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "EcoMotion@123";
    public static final String RENTER_EMAIL = "renter01@ecomotion.vn";
    public static final String PROVIDER_EMAIL = "provider01@ecomotion.vn";
    public static final String VEHICLE_PLATE = "51K-888.88";
    public static final String ACCEPTED_BOOKING_CODE = "EM-2026-9001";

    private static final String VEHICLE_IMAGE =
            "https://images.unsplash.com/photo-1593941707882-a5bba14938c7?auto=format&fit=crop&w=800&q=80";

    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final ProviderRepository providerRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final BookingChargeRepository chargeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User renterUser = seedUser(RENTER_EMAIL, "0901000001", "Renter 01", Role.RENTER);
        User providerUser = seedUser(PROVIDER_EMAIL, "0901000002", "Provider 01", Role.PROVIDER);
        seedUser("admin01@ecomotion.vn", "0901000003", "Admin 01", Role.ADMIN);
        Renter renter = seedRenter(renterUser);
        Provider provider = seedProvider(providerUser);
        Vehicle vehicle = seedVehicle(provider);
        seedAcceptedBooking(renter, renterUser, provider, vehicle);
    }

    private User seedUser(String email, String phone, String fullName, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setPhone(phone);
            user.setFullName(fullName);
            user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            user.setRole(role);
            user.setStatus(UserStatus.ACTIVE);
            user.setEkycStatus(EkycStatus.VERIFIED);
            return userRepository.save(user);
        });
    }

    private Renter seedRenter(User user) {
        return renterRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            Renter renter = new Renter();
            renter.setUser(user);
            return renterRepository.save(renter);
        });
    }

    private Provider seedProvider(User user) {
        return providerRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            Provider provider = new Provider();
            provider.setUser(user);
            provider.setProviderType(ProviderType.INDIVIDUAL);
            provider.setDisplayName("Green Motion Fleet");
            provider.setHotline("19008899");
            provider.setDepotAddress("18 Hoàng Hoa Thám, Phường 13, Quận Tân Bình, TP. Hồ Chí Minh");
            provider.setCommissionRate(new BigDecimal("0.1000"));
            provider.setDepositRatio(new BigDecimal("0.3000"));
            provider.setStatus(ProviderStatus.APPROVED);
            return providerRepository.save(provider);
        });
    }

    private Vehicle seedVehicle(Provider provider) {
        Vehicle vehicle = vehicleRepository.findByLicensePlate(VEHICLE_PLATE).orElseGet(() -> {
            Vehicle created = new Vehicle();
            created.setProvider(provider);
            created.setLicensePlate(VEHICLE_PLATE);
            created.setChassisNo("VF8-CHASSIS-0001");
            created.setEngineNo("VF8-ENGINE-0001");
            created.setBrand("VinFast");
            created.setModel("VF 8 Plus");
            created.setManufactureYear(2024);
            created.setSeatCount(5);
            created.setTransmission(Transmission.AUTOMATIC);
            created.setColor("Xanh");
            created.setFuelType(FuelType.EV);
            created.setStatus(VehicleStatus.APPROVED);
            created.setMaintenanceBufferMinutes(30);
            return vehicleRepository.save(created);
        });
        if (!vehicleImageRepository.existsByVehicle_Id(vehicle.getId())) {
            VehicleImage image = new VehicleImage();
            image.setVehicle(vehicle);
            image.setImageUrl(VEHICLE_IMAGE);
            image.setDisplayOrder(0);
            image.setCaption("VinFast VF 8 Plus");
            vehicleImageRepository.save(image);
        }
        if (!rentalPlanRepository.existsByVehicle_Id(vehicle.getId())) {
            rentalPlanRepository.save(plan(vehicle, PlanType.HOURLY, 60, "180000.00", null, true));
            rentalPlanRepository.save(plan(vehicle, PlanType.DAILY, 1440, "1450000.00", null, false));
            rentalPlanRepository.save(plan(vehicle, PlanType.MONTHLY, 43200, "32000000.00", null, false));
            rentalPlanRepository.save(plan(vehicle, PlanType.YEARLY, 525600, "280000000.00", "50000000.00", false));
        }
        return vehicle;
    }

    private void seedAcceptedBooking(Renter renter, User renterUser, Provider provider, Vehicle vehicle) {
        if (bookingRepository.existsByBookingCode(ACCEPTED_BOOKING_CODE)) {
            return;
        }
        RentalPlan daily = rentalPlanRepository
                .findByVehicle_IdAndStatusOrderByIdAsc(vehicle.getId(), PlanStatus.ACTIVE)
                .stream()
                .filter(plan -> plan.getPlanType() == PlanType.DAILY)
                .findFirst()
                .orElseThrow();

        Booking booking = new Booking();
        booking.setBookingCode(ACCEPTED_BOOKING_CODE);
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(daily);
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setPickupAddress(provider.getDepotAddress());
        booking.setPickupTime(Instant.parse("2026-12-20T01:00:00Z"));
        booking.setPassengerCount(2);
        booking.setTotalAmount(daily.getSlotPrice());
        bookingRepository.save(booking);

        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setSlotPrice(daily.getSlotPrice());
        snapshot.setSlotLengthMinutes(daily.getSlotLengthMinutes());
        snapshot.setPlanType(daily.getPlanType());
        snapshot.setDepositRatio(provider.getDepositRatio());
        snapshot.setUpfrontAmount(null);
        snapshot.setAllowedOvertimeMinutes(daily.getAllowedOvertimeMinutes());
        snapshot.setOvertimeSurchargeRatio(daily.getOvertimeSurchargeRatio());
        snapshot.setWaitingFreeMinutes(daily.getWaitingFreeMinutes());
        snapshot.setWaitingRate(daily.getWaitingRate());
        snapshot.setWeekendSurchargeRatio(daily.getWeekendSurchargeRatio());
        snapshot.setFreeRadiusKm(daily.getFreeRadiusKm());
        snapshot.setOutOfRadiusRate(daily.getOutOfRadiusRate());
        snapshot.setHourlyToDailyThresholdMinutes(daily.getHourlyToDailyThresholdMinutes());
        snapshotRepository.save(snapshot);

        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(ChargeType.BASE_RENTAL);
        charge.setDescription("Thuê 1 slot");
        charge.setQuantity(1);
        charge.setUnitPrice(daily.getSlotPrice());
        charge.setAmount(daily.getSlotPrice());
        charge.setCreatedBy(renterUser);
        charge.setWaived(false);
        chargeRepository.save(charge);
    }

    private RentalPlan plan(Vehicle vehicle,
                            PlanType type,
                            int slotLengthMinutes,
                            String slotPrice,
                            String upfrontAmount,
                            boolean hourly) {
        RentalPlan plan = new RentalPlan();
        plan.setVehicle(vehicle);
        plan.setPlanType(type);
        plan.setSlotLengthMinutes(slotLengthMinutes);
        plan.setSlotPrice(new BigDecimal(slotPrice));
        plan.setUpfrontAmount(upfrontAmount == null ? null : new BigDecimal(upfrontAmount));
        plan.setStatus(PlanStatus.ACTIVE);
        if (type != PlanType.YEARLY) {
            plan.setAllowedOvertimeMinutes(30);
            plan.setOvertimeSurchargeRatio(new BigDecimal("1.2000"));
            plan.setFreeRadiusKm(10);
            plan.setOutOfRadiusRate(new BigDecimal("10000.00"));
            plan.setWaitingFreeMinutes(15);
            plan.setWaitingRate(new BigDecimal("5000.00"));
            plan.setWeekendSurchargeRatio(new BigDecimal("1.1000"));
            if (hourly) {
                plan.setHourlyToDailyThresholdMinutes(1260);
            }
        }
        return plan;
    }
}
