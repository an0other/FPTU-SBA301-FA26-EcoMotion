package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.common.crypto.DeterministicCipher;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "EcoMotion@123";

    private static final long PROVIDER_A_USER_ID = 2L;
    private static final long PROVIDER_B_USER_ID = 4L;

    private static final String NAME_PROVIDER_A = "Nguyễn Văn A";
    private static final String NAME_PROVIDER_B = "Trần Thị B";
    private static final String NAME_RENTER = "Phạm Thu Hà";
    private static final String ADDRESS_A = "12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh";
    private static final String ADDRESS_B = "45 Lê Lợi, Quận 1, TP. Hồ Chí Minh";
    private static final String COLOR_WHITE = "Trắng";
    private static final String COLOR_RED = "Đỏ";
    private static final String COLOR_BLACK = "Đen";
    private static final String DESC_VF6 = "Xe điện 5 chỗ, phù hợp đi nội thành và lân cận.";
    private static final String DESC_VF3 = "Xe điện cỡ nhỏ, tiết kiệm, dễ di chuyển trong phố.";
    private static final String DESC_CIVIC = "Xe thể thao số sàn, phù hợp khách thích trải nghiệm lái.";
    private static final String DESC_CAMRY = "Sedan hybrid hạng D, rộng rãi cho gia đình và công tác.";
    private static final String DESC_BMW = "Sedan sang trọng, phù hợp đưa đón đối tác và sự kiện.";
    private static final String COLOR_SILVER = "Bạc";
    private static final String COLOR_GREY = "Xám";
    private static final String DESC_PORSCHE = "Coupe thể thao 2 cửa, phù hợp khách yêu cảm giác lái.";
    private static final String DESC_CAMRY_XSE = "Sedan hybrid hạng D bản XSE, rộng rãi và tiết kiệm.";
    private static final String DESC_RANGE_ROVER = "SUV hạng sang, nội thất rộng rãi, phù hợp hành trình dài.";

    private static final String UPFRONT_HOURLY = "500000.00";
    private static final String UPFRONT_DAILY = "2000000.00";
    private static final String UPFRONT_MONTHLY = "20000000.00";

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final RenterRepository renterRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeterministicCipher cipher;
    private final EntityManager entityManager;

    private record PlanPrices(String hourly, String daily, String monthly) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser(1L, "renter01@ecomotion.vn", "0901000001", "Renter 01", Role.RENTER);
        seedUser(PROVIDER_A_USER_ID, "provider.a@ecomotion.vn", "0901000010", NAME_PROVIDER_A, Role.PROVIDER);
        seedUser(3L, "admin01@ecomotion.vn", "0901000003", "Admin 01", Role.ADMIN);
        seedUser(PROVIDER_B_USER_ID, "provider.b@ecomotion.vn", "0901000011", NAME_PROVIDER_B, Role.PROVIDER);
        seedUser(5L, "renter.demo@ecomotion.vn", "0901000012", NAME_RENTER, Role.RENTER);

        User providerUserA = userRepository.findById(PROVIDER_A_USER_ID).orElseThrow();
        User providerUserB = userRepository.findById(PROVIDER_B_USER_ID).orElseThrow();
        Provider providerA = seedProvider(providerUserA, "EcoMotion Depot A", ADDRESS_A, "02873000010",
                new BigDecimal("10.773300"), new BigDecimal("106.703600"));
        Provider providerB = seedProvider(providerUserB, "EcoMotion Depot B", ADDRESS_B, "02873000011",
                new BigDecimal("10.774900"), new BigDecimal("106.698500"));

        seedRenterProfile(userRepository.findById(5L).orElseThrow());

        seedVehicle(providerA.getId(), 1L, PROVIDER_A_USER_ID, "VinFast", "VF 6", 2024, 5, Transmission.AUTOMATIC,
                COLOR_WHITE, FuelType.EV, "51A-11111",
                "4.70", 24, new PlanPrices("180000.00", "1100000.00", "16500000.00"),
                List.of(
                        "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114343463-750x375.webp",
                        "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114403238-1024x576.webp",
                        "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114455179-1024x576.webp"),
                DESC_VF6);

        seedVehicle(providerA.getId(), 2L, PROVIDER_A_USER_ID, "VinFast", "VF 3", 2024, 4, Transmission.AUTOMATIC,
                "Xanh", FuelType.EV, "51A-22222",
                "4.90", 41, new PlanPrices("150000.00", "900000.00", "13500000.00"),
                List.of(
                        "VF3_Rear 34_Driver_Side_Wheel_Cover1.webp",
                        "rear-left-view-121.webp",
                        "vf3-6.webp",
                        "vf3_exterior-shot-13-1-2.webp"),
                DESC_VF3);

        seedVehicle(providerA.getId(), 3L, PROVIDER_A_USER_ID, "Honda", "Civic Type R", 2023, 5, Transmission.MANUAL,
                COLOR_RED, FuelType.GASOLINE, "51A-33333",
                "4.60", 9, new PlanPrices("250000.00", "1600000.00", "22000000.00"),
                List.of(
                        "43609-a63d66d270d9ac60349da19c686f83d9.webp",
                        "43842-4edd2197b736d8fb1192cf41a64b8983.webp",
                        "e66cf80620599ab536e4e909fa9ef219.webp",
                        "free-photo-of-red-honda-civic-type-r.webp"),
                DESC_CIVIC);

        seedVehicle(providerA.getId(), 4L, PROVIDER_A_USER_ID, "Toyota", "Camry", 2025, 5, Transmission.AUTOMATIC,
                COLOR_BLACK, FuelType.HYBRID, "51A-44444",
                "4.85", 17, new PlanPrices("220000.00", "1400000.00", "19000000.00"),
                List.of(
                        "2025-toyota-camry-xse-awd-149-66993cc939929.webp",
                        "ToyotaCamry-3720260323_171128.webp",
                        "camry-exterior-closed-hood-bonnet.webp",
                        "camry-exterior-right-front-three-quarter-7.webp"),
                DESC_CAMRY);

        seedVehicle(providerB.getId(), 5L, PROVIDER_B_USER_ID, "BMW", "320i M Sport", 2023, 5, Transmission.AUTOMATIC,
                COLOR_BLACK, FuelType.GASOLINE, "51A-55555",
                "4.95", 33, new PlanPrices("320000.00", "2000000.00", "26000000.00"),
                List.of(
                        "71286676-bmw-320i-black-and-white.webp",
                        "BMW-320i-M-Sport-10th-Anniversary-Edition-29.webp",
                        "BMW-320i-M-Sport-830x415.webp",
                        "wp7439603.webp"),
                DESC_BMW);

        seedVehicle(providerB.getId(), 6L, PROVIDER_B_USER_ID, "Porsche", "911 Carrera", 2024, 4, Transmission.AUTOMATIC,
                COLOR_SILVER, FuelType.GASOLINE, "51A-66666",
                "5.00", 6, new PlanPrices("700000.00", "4500000.00", "25000000.00"),
                List.of(
                        "360_F_469231916_yBXAlwxLVTAvJ5JkyE2PdnjCOXUp2wwE.webp",
                        "Feature-650x420-1-13-8-500x323.webp",
                        "filters_format(avif).webp",
                        "filters_format(webp).webp"),
                DESC_PORSCHE);

        seedVehicle(providerB.getId(), 7L, PROVIDER_B_USER_ID, "Toyota", "Camry XSE", 2025, 5, Transmission.AUTOMATIC,
                COLOR_SILVER, FuelType.HYBRID, "51A-77777",
                "4.40", 12, new PlanPrices("240000.00", "1500000.00", "20000000.00"),
                List.of(
                        "2025-toyota-camry-xse-awd-149-66993cc939929.webp",
                        "ToyotaCamry-2620260323_171018.webp",
                        "ToyotaCamry-3720260323_171128.webp",
                        "camry-exterior-right-front-three-quarter-7.webp"),
                DESC_CAMRY_XSE);

        seedVehicle(providerB.getId(), 8L, PROVIDER_B_USER_ID, "Land Rover", "Range Rover Sport", 2023, 5, Transmission.AUTOMATIC,
                COLOR_GREY, FuelType.GASOLINE, "51A-88888",
                "0.00", 0, new PlanPrices("450000.00", "2800000.00", "38000000.00"),
                List.of(
                        "2022-range-rover-se-lwb-150-1665593833.webp",
                        "L461_23MY_032_GLHD_BORASCO_GREY.webp",
                        "RANGE-ROVER-ELECTRIC-FRONT-3-4-02_09_26_1.jpg_auto=format,compress&fit=crop&h=303&ixlib=php-3.3.webp",
                        "range-rover-sport-in-monte-carlo-monaco.webp"),
                DESC_RANGE_ROVER);

        realignPinnedIdentitySequences();

        seedBookings();
    }

    private void seedBookings() {
        if (bookingRepository.count() > 0) {
            return;
        }

        Renter renter = renterRepository.findByUser_Id(5L).orElse(null);
        Vehicle vehicle1 = vehicleRepository.findById(1L).orElse(null);
        Vehicle vehicle2 = vehicleRepository.findById(2L).orElse(null);
        if (renter == null || vehicle1 == null || vehicle2 == null) {
            return;
        }

        RentalPlan plan1 = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(1L).stream()
                .findFirst().orElse(null);
        RentalPlan plan2 = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(2L).stream()
                .findFirst().orElse(null);
        if (plan1 == null || plan2 == null) {
            return;
        }

        Booking b1 = new Booking();
        b1.setBookingCode("BK-20261008-001");
        b1.setRenter(renter);
        b1.setVehicle(vehicle1);
        b1.setPlan(plan1);
        b1.setStatus(BookingStatus.PENDING);
        b1.setPickupAddress("720A Điện Biên Phủ, Phường 22, Bình Thạnh, TP.HCM");
        b1.setPickupTime(Instant.now().plus(1, ChronoUnit.DAYS));
        b1.setPassengerCount(2);
        b1.setTotalAmount(new BigDecimal("1200000.00"));
        bookingRepository.save(b1);

        Booking b2 = new Booking();
        b2.setBookingCode("BK-20261008-002");
        b2.setRenter(renter);
        b2.setVehicle(vehicle1);
        b2.setPlan(plan1);
        b2.setStatus(BookingStatus.ACCEPTED);
        b2.setPickupAddress("Sân bay Tân Sơn Nhất, Tân Bình, TP.HCM");
        b2.setPickupTime(Instant.now().plus(2, ChronoUnit.DAYS));
        b2.setPassengerCount(4);
        b2.setTotalAmount(new BigDecimal("2400000.00"));
        bookingRepository.save(b2);

        Booking b3 = new Booking();
        b3.setBookingCode("BK-20261008-003");
        b3.setRenter(renter);
        b3.setVehicle(vehicle2);
        b3.setPlan(plan2);
        b3.setStatus(BookingStatus.CONFIRMED);
        b3.setPickupAddress("12 Võ Văn Kiệt, Quận 1, TP.HCM");
        b3.setPickupTime(Instant.now().plus(3, ChronoUnit.DAYS));
        b3.setPassengerCount(3);
        b3.setTotalAmount(new BigDecimal("1800000.00"));
        bookingRepository.save(b3);
    }

    private void realignPinnedIdentitySequences() {
        realignIdentitySequence("users");
        realignIdentitySequence("vehicle");
    }

    private void realignIdentitySequence(String table) {
        Number maxId = (Number) entityManager
                .createNativeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)
                .getSingleResult();
        entityManager
                .createNativeQuery("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH "
                        + (maxId.longValue() + 1L))
                .executeUpdate();
    }

    private User seedUser(long id, String email, String phone, String fullName, Role role) {
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            if (existing.getId() != id) {
                throw new IllegalStateException("Seed user " + email + " exists with id "
                        + existing.getId() + " but images requires id " + id);
            }
            return existing;
        }

        if (userRepository.findById(id).isEmpty()) {
            String phoneCiphertext = cipher.encrypt(phone);
            entityManager.createNativeQuery("""
                            INSERT INTO users
                                (id, email, phone, password_hash, full_name, avatar_url, role, status,
                                 ekyc_status, created_at, updated_at)
                            VALUES
                                (:id, :email, :phone, :passwordHash, :fullName, NULL, :role, :status,
                                 :ekycStatus, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .setParameter("id", id)
                    .setParameter("email", email)
                    .setParameter("phone", phoneCiphertext)
                    .setParameter("passwordHash", passwordEncoder.encode(DEFAULT_PASSWORD))
                    .setParameter("fullName", fullName)
                    .setParameter("role", role.name())
                    .setParameter("status", UserStatus.ACTIVE.name())
                    .setParameter("ekycStatus", EkycStatus.VERIFIED.name())
                    .executeUpdate();
        }

        return userRepository.findById(id).orElseThrow();
    }

    private Provider seedProvider(User user, String displayName, String depotAddress, String hotline,
                                  BigDecimal latitude, BigDecimal longitude) {
        Provider existing = providerRepository.findByUser_Id(user.getId()).orElse(null);
        if (existing != null) {
            boolean changed = false;
            if ("EcoMotion Depot B".equals(existing.getDisplayName())
                    && "45 Lê Lợi, Quận 3, TP. Hồ Chí Minh".equals(existing.getDepotAddress())) {
                existing.setDepotAddress(ADDRESS_B);
                changed = true;
            }
            if (existing.getDepotAddress() == null || existing.getDepotAddress().isBlank()) {
                existing.setDepotAddress(depotAddress);
                changed = true;
                if (existing.getHotline() == null || existing.getHotline().isBlank()) {
                    existing.setHotline(hotline);
                }
            }
            if (existing.getDepotLatitude() == null && existing.getDepotLongitude() == null
                    && depotAddress.equals(existing.getDepotAddress())) {
                existing.setDepotLatitude(latitude);
                existing.setDepotLongitude(longitude);
                changed = true;
            }
            return changed ? providerRepository.save(existing) : existing;
        }

        Provider provider = new Provider();
        provider.setUser(user);
        provider.setProviderType(ProviderType.BUSINESS);
        provider.setDisplayName(displayName);
        provider.setHotline(hotline);
        provider.setDepotAddress(depotAddress);
        provider.setDepotLatitude(latitude);
        provider.setDepotLongitude(longitude);
        provider.setCommissionRate(new BigDecimal("0.1000"));
        provider.setDepositRatio(new BigDecimal("0.3000"));
        provider.setStatus(ProviderStatus.APPROVED);
        provider.setRatingAvg(new BigDecimal("5.00"));
        provider.setRatingCount(2);
        return providerRepository.save(provider);
    }

    private void seedRenterProfile(User user) {
        if (renterRepository.existsByUser_Id(user.getId())) {
            return;
        }
        Renter renter = new Renter();
        renter.setUser(user);
        renterRepository.save(renter);
    }

    private void seedVehicle(Long providerId, Long vehicleId, Long providerUserId,
                             String brand, String model, int year, int seats,
                             Transmission transmission, String color, FuelType fuelType,
                             String licensePlate,
                             String ratingAvg, int ratingCount, PlanPrices prices,
                             List<String> imageFiles, String description) {
        if (vehicleRepository.findById(vehicleId).isEmpty()) {
            entityManager.createNativeQuery("""
                            INSERT INTO vehicle
                                (id, provider_id, license_plate, chassis_no, engine_no, brand, model,
                                 manufacture_year, seat_count, transmission, color, fuel_type, status,
                                 maintenance_buffer_minutes, rating_avg, rating_count, created_at, updated_at)
                            VALUES
                                (:id, :providerId, :licensePlate, :chassisNo, :engineNo, :brand, :model,
                                 :year, :seats, :transmission, :color, :fuelType, :status,
                                 60, :ratingAvg, :ratingCount, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .setParameter("id", vehicleId)
                    .setParameter("providerId", providerId)
                    .setParameter("licensePlate", licensePlate)
                    .setParameter("chassisNo", "CHASSIS-" + brand.toUpperCase() + "-" + vehicleId)
                    .setParameter("engineNo", "ENGINE-" + brand.toUpperCase() + "-" + vehicleId)
                    .setParameter("brand", brand)
                    .setParameter("model", model)
                    .setParameter("year", year)
                    .setParameter("seats", seats)
                    .setParameter("transmission", transmission.name())
                    .setParameter("color", color)
                    .setParameter("fuelType", fuelType.name())
                    .setParameter("status", VehicleStatus.APPROVED.name())
                    .setParameter("ratingAvg", new BigDecimal(ratingAvg))
                    .setParameter("ratingCount", ratingCount)
                    .executeUpdate();
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow();

        seedImages(vehicle, providerUserId, imageFiles, description);
        seedPlans(vehicle, prices);
    }

    private void seedImages(Vehicle vehicle, Long providerUserId, List<String> imageFiles, String description) {
        if (!vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(vehicle.getId()).isEmpty()) {
            return;
        }
        int order = 0;
        for (String file : imageFiles) {
            VehicleImage image = new VehicleImage();
            image.setVehicle(vehicle);
            image.setImageUrl("/images/" + providerUserId + "/" + vehicle.getId() + "/" + encodePathSegment(file));
            image.setDisplayOrder(order);
            image.setCaption(order == 0 ? description : null);
            vehicleImageRepository.save(image);
            order++;
        }
    }

    private void seedPlans(Vehicle vehicle, PlanPrices prices) {
        if (!rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).isEmpty()) {
            return;
        }
        createPlan(vehicle, PlanType.HOURLY, 240, prices.hourly(), UPFRONT_HOURLY);
        createPlan(vehicle, PlanType.DAILY, 1440, prices.daily(), UPFRONT_DAILY);
        createPlan(vehicle, PlanType.MONTHLY, 43200, prices.monthly(), UPFRONT_MONTHLY);
    }

    private void createPlan(Vehicle vehicle, PlanType planType, int slotLengthMinutes,
                            String slotPrice, String upfrontAmount) {
        RentalPlan plan = new RentalPlan();
        plan.setVehicle(vehicle);
        plan.setPlanType(planType);
        plan.setSlotLengthMinutes(slotLengthMinutes);
        plan.setSlotPrice(new BigDecimal(slotPrice));
        plan.setUpfrontAmount(new BigDecimal(upfrontAmount));
        plan.setStatus(PlanStatus.ACTIVE);
        rentalPlanRepository.save(plan);
    }

    private static String encodePathSegment(String segment) {
        return segment.replace(" ", "%20");
    }
}
