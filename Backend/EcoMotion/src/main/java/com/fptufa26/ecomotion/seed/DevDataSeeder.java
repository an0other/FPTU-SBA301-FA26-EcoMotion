package com.fptufa26.ecomotion.seed;

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

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "EcoMotion@123";
    public static final String RENTER_EMAIL = "renter01@ecomotion.vn";
    public static final String PROVIDER_EMAIL = "provider01@ecomotion.vn";
    public static final String VEHICLE_PLATE = "51K-888.88";

    private static final String VEHICLE_IMAGE =
            "https://images.unsplash.com/photo-1593941707882-a5bba14938c7?auto=format&fit=crop&w=800&q=80";

    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final ProviderRepository providerRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User renterUser = seedUser(RENTER_EMAIL, "0901000001", "Renter 01", Role.RENTER);
        User providerUser = seedUser(PROVIDER_EMAIL, "0901000002", "Provider 01", Role.PROVIDER);
        seedUser("admin01@ecomotion.vn", "0901000003", "Admin 01", Role.ADMIN);
        seedRenter(renterUser);
        Provider provider = seedProvider(providerUser);
        seedVehicle(provider);
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

    private void seedRenter(User user) {
        if (renterRepository.existsByUser_Id(user.getId())) {
            return;
        }
        Renter renter = new Renter();
        renter.setUser(user);
        renterRepository.save(renter);
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

    private void seedVehicle(Provider provider) {
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
