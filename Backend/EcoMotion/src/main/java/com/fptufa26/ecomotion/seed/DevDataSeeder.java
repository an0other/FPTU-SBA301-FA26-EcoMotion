package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import java.math.RoundingMode;
import java.time.Duration;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.common.crypto.DeterministicCipher;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {
    public static final String RENTER_EMAIL = "renter01@ecomotion.vn";
    public static final String PROVIDER_EMAIL = "provider.a@ecomotion.vn";
    public static final String VEHICLE_PLATE = "51A-11111";
    public static final String ACCEPTED_BOOKING_CODE = "EM-2026-9001";
    public static final String PENDING_BOOKING_CODE = "EM-2026-9002";
    public static final String IN_PROGRESS_BOOKING_CODE = "EM-2026-9003";

        public static final String DEFAULT_PASSWORD = "EcoMotion@123";

        private static final long PROVIDER_A_USER_ID = 2L;
        private static final long PROVIDER_B_USER_ID = 4L;
    private final RenterPaymentDataSeeder renterPaymentDataSeeder;
    private final RenterReviewDataSeeder renterReviewDataSeeder;


        private static final String NAME_PROVIDER_A = "Nguy\u1ec5n V\u0103n A";
        private static final String NAME_PROVIDER_B = "Tr\u1ea7n Th\u1ecb B";
        private static final String NAME_RENTER = "Ph\u1ea1m Thu H\u00e0";
        private static final String ADDRESS_A = "12 Nguy\u1ec5n Hu\u1ec7, Qu\u1eadn 1, TP. H\u1ed3 Ch\u00ed Minh";
        private static final String ADDRESS_B = "45 L\u00ea L\u1ee3i, Qu\u1eadn 3, TP. H\u1ed3 Ch\u00ed Minh";
        private static final String COLOR_WHITE = "Tr\u1eafng";
        private static final String COLOR_RED = "\u0110\u1ecf";
        private static final String COLOR_BLACK = "\u0110en";
        private static final String COLOR_SILVER = "B\u1ea1c";
        private static final String COLOR_GREY = "X\u00e1m";

        private static final String DESC_VF6 = "Xe \u0111i\u1ec7n 5 ch\u1ed7, ph\u00f9 h\u1ee3p \u0111i n\u1ed9i th\u00e0nh v\u00e0 l\u00e2n c\u1eadn.";
        private static final String DESC_VF3 = "Xe \u0111i\u1ec7n c\u1ee1 nh\u1ecf, ti\u1ebft ki\u1ec7m, d\u1ec5 di chuy\u1ec3n trong ph\u1ed1.";
        private static final String DESC_CIVIC = "Xe th\u1ec3 thao s\u1ed1 s\u00e0n, ph\u00f9 h\u1ee3p kh\u00e1ch th\u00edch tr\u1ea3i nghi\u1ec7m l\u00e1i.";
        private static final String DESC_CAMRY = "Sedan hybrid h\u1ea1ng D, r\u1ed9ng r\u00e3i cho gia \u0111\u00ecnh v\u00e0 c\u00f4ng t\u00e1c.";
        private static final String DESC_BMW = "Sedan sang tr\u1ecdng, ph\u00f9 h\u1ee3p \u0111\u01b0a \u0111\u00f3n \u0111\u1ed1i t\u00e1c v\u00e0 s\u1ef1 ki\u1ec7n.";
        private static final String DESC_PORSCHE = "Coupe th\u1ec3 thao 2 c\u1eeda, ph\u00f9 h\u1ee3p kh\u00e1ch y\u00eau c\u1ea3m gi\u00e1c l\u00e1i.";
        private static final String DESC_CAMRY_XSE = "Sedan hybrid h\u1ea1ng D b\u1ea3n XSE, r\u1ed9ng r\u00e3i v\u00e0 ti\u1ebft ki\u1ec7m.";
        private static final String DESC_RANGE_ROVER = "SUV h\u1ea1ng sang, n\u1ed9i th\u1ea5t r\u1ed9ng r\u00e3i, ph\u00f9 h\u1ee3p h\u00e0nh tr\u00ecnh d\u00e0i.";

        private static final String UPFRONT_HOURLY = "500000.00";
        private static final String UPFRONT_DAILY = "2000000.00";
        private static final String UPFRONT_MONTHLY = "20000000.00";

        private final UserRepository userRepository;
        private final ProviderRepository providerRepository;
        private final RenterRepository renterRepository;
        private final DriverRepository driverRepository;
        private final VehicleRepository vehicleRepository;
        private final VehicleImageRepository vehicleImageRepository;
        private final RentalPlanRepository rentalPlanRepository;
        private final BookingRepository bookingRepository;
        private final PasswordEncoder passwordEncoder;
        private final DeterministicCipher cipher;
        private final EntityManager entityManager;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final BookingChargeRepository chargeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ScheduleService scheduleService;

        private record PlanPrices(String hourly, String daily, String monthly) {
        }

        @Override
        @Transactional
        public void run(ApplicationArguments args) {
                // Core users
                seedUser(1L, "renter01@ecomotion.vn", "0901000001", "Renter 01", Role.RENTER);
                seedUser(PROVIDER_A_USER_ID, "provider.a@ecomotion.vn", "0901000010", NAME_PROVIDER_A, Role.PROVIDER);
                seedUser(3L, "admin01@ecomotion.vn", "0901000003", "Admin 01", Role.ADMIN);
                seedUser(PROVIDER_B_USER_ID, "provider.b@ecomotion.vn", "0901000011", NAME_PROVIDER_B, Role.PROVIDER);
                seedUser(5L, "renter.demo@ecomotion.vn", "0901000012", NAME_RENTER, Role.RENTER);

                // Additional Renters
                seedUser(101L, "duc.tran@ecomotion.vn", "0901000021", "Tr\u1ea7n Minh \u0110\u1ee9c", Role.RENTER);
                seedUser(102L, "nam.le@ecomotion.vn", "0901000022", "L\u00ea Ho\u00e0ng Nam", Role.RENTER);
                seedUser(103L, "kieu.vo@ecomotion.vn", "0901000023", "V\u00f5 Th\u1ee5y Ki\u1ec1u", Role.RENTER);
                seedUser(104L, "anh.dang@ecomotion.vn", "0901000024", "\u0110\u1eb7ng Qu\u1ed1c Anh", Role.RENTER);
                seedUser(105L, "long.hoang@ecomotion.vn", "0901000025", "Ho\u00e0ng B\u1ea3o Long", Role.RENTER);
                seedUser(106L, "cuong.nguyen@ecomotion.vn", "0901000026", "Nguy\u1ec5n Vi\u1ec7t C\u01b0\u1eddng",
                                Role.RENTER);
                seedUser(107L, "mai.truong@ecomotion.vn", "0901000027", "Tr\u01b0\u01a1ng Thanh Mai", Role.RENTER);

                // Drivers for Provider A
                User driverUser1 = seedUser(201L, "driver.nam@ecomotion.vn", "0901000031", "Nguy\u1ec5n V\u0103n Nam",
                                Role.DRIVER);
                User driverUser2 = seedUser(202L, "driver.cuong@ecomotion.vn", "0901000032",
                                "Tr\u01b0\u01a1ng H\u00f9ng C\u01b0\u1eddng", Role.DRIVER);
                User driverUser3 = seedUser(203L, "driver.kiet@ecomotion.vn", "0901000033",
                                "V\u0169 Tu\u1ea5n Ki\u1ec7t", Role.DRIVER);

                User providerUserA = userRepository.findById(PROVIDER_A_USER_ID).orElseThrow();
                User providerUserB = userRepository.findById(PROVIDER_B_USER_ID).orElseThrow();
                Provider providerA = seedProvider(providerUserA, "EcoMotion Depot A", ADDRESS_A, "02873000010", new BigDecimal("10.773300"), new BigDecimal("106.703600"));
                Provider providerB = seedProvider(providerUserB, "EcoMotion Depot B", ADDRESS_B, "02873000011", new BigDecimal("10.774900"), new BigDecimal("106.698500"));

                // Seed Renter profiles
                seedRenterProfile(userRepository.findById(1L).orElseThrow());
                seedRenterProfile(userRepository.findById(5L).orElseThrow());
                seedRenterProfile(userRepository.findById(101L).orElseThrow());
                seedRenterProfile(userRepository.findById(102L).orElseThrow());
                seedRenterProfile(userRepository.findById(103L).orElseThrow());
                seedRenterProfile(userRepository.findById(104L).orElseThrow());
                seedRenterProfile(userRepository.findById(105L).orElseThrow());
                seedRenterProfile(userRepository.findById(106L).orElseThrow());
                seedRenterProfile(userRepository.findById(107L).orElseThrow());

                // Seed Drivers for Provider A
                Driver driverNam = seedDriver(driverUser1, providerA, "7901230001", "B2");
                Driver driverCuong = seedDriver(driverUser2, providerA, "7901230002", "D");
                Driver driverKiet = seedDriver(driverUser3, providerA, "7901230003", "B2");

                // Seed exactly the 4 vehicles for Provider A (pinned fleet size = 8 across
                // provider A and B)
                seedVehicle(providerA.getId(), 1L, PROVIDER_A_USER_ID, "VinFast", "VF 6", 2024, 5,
                                Transmission.AUTOMATIC,
                                COLOR_WHITE, FuelType.EV, "51A-11111",
                                "4.70", 24, new PlanPrices("180000.00", "1100000.00", "16500000.00"),
                                List.of(
                                                "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114343463-750x375.webp",
                                                "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114403238-1024x576.webp",
                                                "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114455179-1024x576.webp"),
                                DESC_VF6);

                seedVehicle(providerA.getId(), 2L, PROVIDER_A_USER_ID, "VinFast", "VF 3", 2024, 4,
                                Transmission.AUTOMATIC,
                                "Xanh", FuelType.EV, "51A-22222",
                                "4.90", 41, new PlanPrices("150000.00", "900000.00", "13500000.00"),
                                List.of(
                                                "VF3_Rear 34_Driver_Side_Wheel_Cover1.webp",
                                                "rear-left-view-121.webp",
                                                "vf3-6.webp",
                                                "vf3_exterior-shot-13-1-2.webp"),
                                DESC_VF3);

                seedVehicle(providerA.getId(), 3L, PROVIDER_A_USER_ID, "Honda", "Civic Type R", 2023, 5,
                                Transmission.MANUAL,
                                COLOR_RED, FuelType.GASOLINE, "51A-33333",
                                "4.60", 9, new PlanPrices("250000.00", "1600000.00", "22000000.00"),
                                List.of(
                                                "43609-a63d66d270d9ac60349da19c686f83d9.webp",
                                                "43842-4edd2197b736d8fb1192cf41a64b8983.webp",
                                                "e66cf80620599ab536e4e909fa9ef219.webp",
                                                "free-photo-of-red-honda-civic-type-r.webp"),
                                DESC_CIVIC);

                seedVehicle(providerA.getId(), 4L, PROVIDER_A_USER_ID, "Toyota", "Camry", 2025, 5,
                                Transmission.AUTOMATIC,
                                COLOR_BLACK, FuelType.HYBRID, "51A-44444",
                                "4.85", 17, new PlanPrices("220000.00", "1400000.00", "19000000.00"),
                                List.of(
                                                "2025-toyota-camry-xse-awd-149-66993cc939929.webp",
                                                "ToyotaCamry-3720260323_171128.webp",
                                                "camry-exterior-closed-hood-bonnet.webp",
                                                "camry-exterior-right-front-three-quarter-7.webp"),
                                DESC_CAMRY);

                // Seed Vehicles for Provider B (vehicles 5..8)
                seedVehicle(providerB.getId(), 5L, PROVIDER_B_USER_ID, "BMW", "320i M Sport", 2023, 5,
                                Transmission.AUTOMATIC,
                                COLOR_BLACK, FuelType.GASOLINE, "51A-55555",
                                "4.95", 33, new PlanPrices("320000.00", "2000000.00", "26000000.00"),
                                List.of(
                                                "71286676-bmw-320i-black-and-white.webp",
                                                "BMW-320i-M-Sport-10th-Anniversary-Edition-29.webp",
                                                "BMW-320i-M-Sport-830x415.webp",
                                                "wp7439603.webp"),
                                DESC_BMW);

                seedVehicle(providerB.getId(), 6L, PROVIDER_B_USER_ID, "Porsche", "911 Carrera", 2024, 4,
                                Transmission.AUTOMATIC,
                                COLOR_SILVER, FuelType.GASOLINE, "51A-66666",
                                "5.00", 6, new PlanPrices("700000.00", "4500000.00", "25000000.00"),
                                List.of(
                                                "360_F_469231916_yBXAlwxLVTAvJ5JkyE2PdnjCOXUp2wwE.webp",
                                                "Feature-650x420-1-13-8-500x323.webp",
                                                "filters_format(avif).webp",
                                                "filters_format(webp).webp"),
                                DESC_PORSCHE);

                seedVehicle(providerB.getId(), 7L, PROVIDER_B_USER_ID, "Toyota", "Camry XSE", 2025, 5,
                                Transmission.AUTOMATIC,
                                COLOR_SILVER, FuelType.HYBRID, "51A-77777",
                                "4.40", 12, new PlanPrices("240000.00", "1500000.00", "20000000.00"),
                                List.of(
                                                "2025-toyota-camry-xse-awd-149-66993cc939929.webp",
                                                "ToyotaCamry-2620260323_171018.webp",
                                                "ToyotaCamry-3720260323_171128.webp",
                                                "camry-exterior-right-front-three-quarter-7.webp"),
                                DESC_CAMRY_XSE);

                seedVehicle(providerB.getId(), 8L, PROVIDER_B_USER_ID, "Land Rover", "Range Rover Sport", 2023, 5,
                                Transmission.AUTOMATIC,
                                COLOR_GREY, FuelType.GASOLINE, "51A-88888",
                                "0.00", 0, new PlanPrices("450000.00", "2800000.00", "38000000.00"),
                                List.of(
                                                "2022-range-rover-se-lwb-150-1665593833.webp",
                                                "L461_23MY_032_GLHD_BORASCO_GREY.webp",
                                                "RANGE-ROVER-ELECTRIC-FRONT-3-4-02_09_26_1.jpg_auto=format,compress&fit=crop&h=303&ixlib=php-3.3.webp",
                                                "range-rover-sport-in-monte-carlo-monaco.webp"),
                                DESC_RANGE_ROVER);

                // Seed 100% Real Bookings into Database across Provider A's 4 vehicles
                User renterUser = userRepository.findById(1L).orElseThrow();
                Renter renter = renterRepository.findByUser_Id(renterUser.getId()).orElseThrow();
                Vehicle bookingVehicle = vehicleRepository.findByLicensePlate(VEHICLE_PLATE).orElseThrow();
                seedSampleBooking(renter, renterUser, providerA, bookingVehicle,
                        ACCEPTED_BOOKING_CODE, BookingStatus.ACCEPTED, Instant.parse("2026-12-20T01:00:00Z"));
                seedSampleBooking(renter, renterUser, providerA, bookingVehicle,
                        PENDING_BOOKING_CODE, BookingStatus.PENDING, Instant.parse("2026-12-28T01:00:00Z"));
                seedInProgressBooking(renter, renterUser, providerA, bookingVehicle);
                seedBookings(driverNam, driverCuong, driverKiet);

                realignPinnedIdentitySequences();

        realignPinnedIdentitySequences();
        renterPaymentDataSeeder.seed();
        realignPinnedIdentitySequences();
        renterPaymentDataSeeder.seed();
        renterReviewDataSeeder.seed();
    }

        private void seedBookings(Driver driverNam, Driver driverCuong, Driver driverKiet) {
                Instant now = Instant.now();

                // PENDING Bookings (3) for Provider A
                seedBooking("BK-8F3K29", 101L, 4L, PlanType.DAILY, null, BookingStatus.PENDING,
                                "214 Nguy\u1ec5n Tr\u00e3i, P. Ph\u1ea1m Ng\u0169 L\u00e3o, Q.1",
                                now.plus(1, ChronoUnit.DAYS),
                                3, "1400000.00", null, null, null, null, null);

                seedBooking("BK-7N2W90", 102L, 1L, PlanType.HOURLY, null, BookingStatus.PENDING,
                                "Landmark 81, Vinhomes Central Park", now.plus(2, ChronoUnit.DAYS),
                                1, "720000.00", null, null, null, null, null);

                seedBooking("BK-3D9F12", 5L, 2L, PlanType.DAILY, null, BookingStatus.PENDING,
                                "123 Nguy\u1ec5n Th\u1ecb Minh Khai, Q.3", now.plus(12, ChronoUnit.HOURS),
                                2, "900000.00", null, null, null, null, null);

                // ACCEPTED Bookings (4) for Provider A
                seedBooking("BK-4M1P88", 103L, 3L, PlanType.DAILY, null, BookingStatus.ACCEPTED,
                                "Ga Qu\u1ed1c t\u1ebf T\u00e2n S\u01a1n Nh\u1ea5t (C\u1ed9t 12)",
                                now.plus(2, ChronoUnit.DAYS),
                                4, "3200000.00", null, null, null, null, null);

                seedBooking("BK-4A2K91", 104L, 3L, PlanType.DAILY, null, BookingStatus.ACCEPTED,
                                "Bitexco Financial Tower, Q.1", now.plus(1, ChronoUnit.DAYS),
                                2, "1600000.00", null, null, null, null, null);

                seedBooking("BK-4B3L92", 105L, 2L, PlanType.HOURLY, null, BookingStatus.ACCEPTED,
                                "Estella Heights, An Ph\u00fa, TP. Th\u1ee7 \u0110\u1ee9c",
                                now.plus(18, ChronoUnit.HOURS),
                                1, "600000.00", null, null, null, null, null);

                seedBooking("BK-4C4M93", 101L, 4L, PlanType.DAILY, null, BookingStatus.ACCEPTED,
                                "Kumho Asiana Plaza, L\u00ea Du\u1ea9n, Q.1", now.plus(3, ChronoUnit.DAYS),
                                3, "1400000.00", null, null, null, null, null);

                // CONFIRMED Bookings (5) for Provider A
                seedBooking("BK-9Q2Z11", 104L, 4L, PlanType.DAILY, driverNam, BookingStatus.CONFIRMED,
                                "Sheraton Saigon, 88 \u0110\u1ed3ng Kh\u1edfi, Q.1", now.plus(1, ChronoUnit.DAYS),
                                2, "1400000.00", null, null, null, null, null);

                seedBooking("BK-5A1X71", 103L, 1L, PlanType.DAILY, driverCuong, BookingStatus.CONFIRMED,
                                "S\u00e2n bay T\u00e2n S\u01a1n Nh\u1ea5t - Ga Qu\u1ed1c n\u1ed9i",
                                now.plus(20, ChronoUnit.HOURS),
                                5, "2200000.00", null, null, null, null, null);

                seedBooking("BK-5B2Y72", 102L, 2L, PlanType.HOURLY, driverKiet, BookingStatus.CONFIRMED,
                                "Saigon Centre, 65 L\u00ea L\u1ee3i, Q.1", now.plus(16, ChronoUnit.HOURS),
                                1, "600000.00", null, null, null, null, null);

                seedBooking("BK-5C3Z73", 5L, 4L, PlanType.DAILY, driverNam, BookingStatus.CONFIRMED,
                                "Diamond Island, TP. Th\u1ee7 \u0110\u1ee9c", now.plus(2, ChronoUnit.DAYS),
                                3, "2800000.00", null, null, null, null, null);

                seedBooking("BK-5D4W74", 105L, 3L, PlanType.DAILY, driverKiet, BookingStatus.CONFIRMED,
                                "Park Hyatt Saigon, Q.1", now.plus(3, ChronoUnit.DAYS),
                                4, "3200000.00", null, null, null, null, null);

                // IN_PROGRESS Bookings (2) for Provider A
                seedBooking("BK-1X8C44", 5L, 4L, PlanType.DAILY, driverCuong, BookingStatus.IN_PROGRESS,
                                "Tuy\u1ebfn: TP.HCM - H\u1ed3 Tr\u00e0m Resort", now.minus(3, ChronoUnit.HOURS),
                                5, "2800000.00", now.minus(3, ChronoUnit.HOURS), null, null, null, null);

                seedBooking("BK-1P9A55", 101L, 1L, PlanType.HOURLY, driverNam, BookingStatus.IN_PROGRESS,
                                "Khu C\u00f4ng Ngh\u1ec7 Cao, TP. Th\u1ee7 \u0110\u1ee9c",
                                now.minus(1, ChronoUnit.HOURS),
                                2, "720000.00", now.minus(1, ChronoUnit.HOURS), null, null, null, null);

                // COMPLETED Bookings (11) for Provider A
                seedBooking("BK-6T9V32", 105L, 4L, PlanType.HOURLY, driverKiet, BookingStatus.COMPLETED,
                                "Khu \u0111\u00f4 th\u1ecb Sala, TP. Th\u1ee7 \u0110\u1ee9c",
                                now.minus(3, ChronoUnit.DAYS),
                                2, "880000.00", now.minus(3, ChronoUnit.DAYS),
                                now.minus(3, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS), null, null, null);

                seedBooking("BK-6A1M01", 101L, 1L, PlanType.DAILY, driverCuong, BookingStatus.COMPLETED,
                                "S\u00e2n Golf T\u00e2n S\u01a1n Nh\u1ea5t", now.minus(4, ChronoUnit.DAYS),
                                4, "1100000.00", now.minus(4, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS), null,
                                null, null);

                seedBooking("BK-6B2N02", 102L, 2L, PlanType.HOURLY, driverNam, BookingStatus.COMPLETED,
                                "Vincom Center Landmark 81", now.minus(5, ChronoUnit.DAYS),
                                1, "600000.00", now.minus(5, ChronoUnit.DAYS),
                                now.minus(5, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS), null, null, null);

                seedBooking("BK-6C3P03", 103L, 3L, PlanType.DAILY, driverKiet, BookingStatus.COMPLETED,
                                "M\u0169i N\u00e9, Phan Thi\u1ebft", now.minus(6, ChronoUnit.DAYS),
                                2, "3200000.00", now.minus(6, ChronoUnit.DAYS), now.minus(4, ChronoUnit.DAYS), null,
                                null, null);

                seedBooking("BK-6D4Q04", 104L, 4L, PlanType.DAILY, driverNam, BookingStatus.COMPLETED,
                                "InterContinental Saigon", now.minus(7, ChronoUnit.DAYS),
                                2, "1400000.00", now.minus(7, ChronoUnit.DAYS), now.minus(6, ChronoUnit.DAYS), null,
                                null, null);

                seedBooking("BK-6E5R05", 5L, 1L, PlanType.DAILY, driverCuong, BookingStatus.COMPLETED,
                                "Aeon Mall T\u00e2n Ph\u00fa", now.minus(8, ChronoUnit.DAYS),
                                3, "1100000.00", now.minus(8, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS), null,
                                null, null);

                seedBooking("BK-6F6S06", 105L, 2L, PlanType.HOURLY, null, BookingStatus.COMPLETED,
                                "Gigamall Th\u1ee7 \u0110\u1ee9c", now.minus(9, ChronoUnit.DAYS),
                                2, "450000.00", now.minus(9, ChronoUnit.DAYS),
                                now.minus(9, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS), null, null, null);

                seedBooking("BK-6G7T07", 106L, 4L, PlanType.DAILY, driverKiet, BookingStatus.COMPLETED,
                                "Khu du l\u1ecbch \u0110\u1ea1i Nam", now.minus(10, ChronoUnit.DAYS),
                                5, "1400000.00", now.minus(10, ChronoUnit.DAYS), now.minus(9, ChronoUnit.DAYS), null,
                                null, null);

                seedBooking("BK-6H8U08", 107L, 1L, PlanType.HOURLY, driverNam, BookingStatus.COMPLETED,
                                "Takashimaya Saigon", now.minus(11, ChronoUnit.DAYS),
                                1, "720000.00", now.minus(11, ChronoUnit.DAYS),
                                now.minus(11, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS), null, null, null);

                seedBooking("BK-6J9V09", 101L, 3L, PlanType.DAILY, driverCuong, BookingStatus.COMPLETED,
                                "V\u0169ng T\u00e0u - B\u00e3i Sau", now.minus(12, ChronoUnit.DAYS),
                                4, "3200000.00", now.minus(12, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS), null,
                                null, null);

                seedBooking("BK-6K0W10", 102L, 3L, PlanType.DAILY, null, BookingStatus.COMPLETED,
                                "Cao t\u1ed1c Long Th\u00e0nh - D\u1ea7u Gi\u00e2y", now.minus(13, ChronoUnit.DAYS),
                                2, "1600000.00", now.minus(13, ChronoUnit.DAYS), now.minus(12, ChronoUnit.DAYS), null,
                                null, null);

                // REJECTED Booking (1) for Provider A
                seedBooking("BK-2R5Y78", 106L, 4L, PlanType.DAILY, null, BookingStatus.REJECTED,
                                "B\u1ebfn Xe Mi\u1ec1n \u0110\u00f4ng M\u1edbi", now.minus(2, ChronoUnit.DAYS),
                                4, "1400000.00", null, null, null, null, null);

                // CANCELLED Bookings (2) for Provider A
                seedBooking("BK-5L3M91", 107L, 1L, PlanType.HOURLY, null, BookingStatus.CANCELLED,
                                "Th\u1ea3o \u0110i\u1ec1n Pearl, TP. Th\u1ee7 \u0110\u1ee9c",
                                now.minus(1, ChronoUnit.DAYS),
                                1, "540000.00", null, null, 107L, CancelReason.RENTER_REQUEST,
                                now.minus(1, ChronoUnit.DAYS));

                seedBooking("BK-5N4P92", 102L, 3L, PlanType.DAILY, null, BookingStatus.CANCELLED,
                                "Crescent Mall, Ph\u00fa M\u1ef9 H\u01b0ng, Q.7", now.minus(4, ChronoUnit.DAYS),
                                2, "1600000.00", null, null, 102L, CancelReason.RENTER_REQUEST,
                                now.minus(4, ChronoUnit.DAYS));

                // Provider B Bookings (2)
                seedBooking("BK-B0A111", 101L, 5L, PlanType.DAILY, null, BookingStatus.PENDING,
                                "1 L\u00ea Du\u1ea9n, Q.1", now.plus(1, ChronoUnit.DAYS),
                                2, "2000000.00", null, null, null, null, null);

                seedBooking("BK-B0A222", 103L, 6L, PlanType.DAILY, null, BookingStatus.ACCEPTED,
                                "72 L\u00ea Th\u00e1nh T\u00f4n, Q.1", now.plus(2, ChronoUnit.DAYS),
                                2, "4500000.00", null, null, null, null, null);
        }

        private void seedBooking(String bookingCode, Long renterUserId, Long vehicleId, PlanType planType,
                        Driver driver, BookingStatus status,
                        String pickupAddress, Instant pickupTime,
                        int passengerCount, String totalAmountStr,
                        Instant actualStartAt, Instant actualEndAt,
                        Long cancelledByUserId, CancelReason cancelReason, Instant cancelledAt) {
                if (bookingRepository.existsByBookingCode(bookingCode)) {
                        return;
                }

                Renter renter = renterRepository.findByUser_Id(renterUserId).orElseThrow();
                Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow();
                RentalPlan plan = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicleId)
                                .stream()
                                .filter(p -> p.getPlanType() == planType)
                                .findFirst()
                                .orElseGet(() -> rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicleId).get(0));

                Booking booking = new Booking();
                booking.setBookingCode(bookingCode);
                booking.setRenter(renter);
                booking.setVehicle(vehicle);
                booking.setPlan(plan);
                booking.setDriver(driver);
                booking.setStatus(status);
                booking.setPickupAddress(pickupAddress);
                booking.setPickupTime(pickupTime);
                booking.setPassengerCount(passengerCount);
                booking.setTotalAmount(new BigDecimal(totalAmountStr));
                booking.setActualStartAt(actualStartAt);
                booking.setActualEndAt(actualEndAt);

                if (status == BookingStatus.CANCELLED) {
                        User cancelledBy = userRepository
                                        .findById(cancelledByUserId != null ? cancelledByUserId : renterUserId)
                                        .orElse(null);
                        booking.setCancelledBy(cancelledBy);
                        booking.setCancelReason(cancelReason != null ? cancelReason : CancelReason.RENTER_REQUEST);
                        booking.setCancelledAt(cancelledAt != null ? cancelledAt : Instant.now());
                }

                bookingRepository.save(booking);
        }

        private Driver seedDriver(User user, Provider provider, String licenseNo, String licenseClass) {
                Driver existing = driverRepository.findByUser_Id(user.getId()).orElse(null);
                if (existing != null) {
                        return existing;
                }
                Driver driver = new Driver();
                driver.setUser(user);
                driver.setProvider(provider);
                driver.setLicenseNo(licenseNo);
                driver.setLicenseClass(licenseClass);
                driver.setLicenseExpiry(LocalDate.now().plusYears(3));
                driver.setPortraitUrl("/images/portrait-" + user.getId() + ".webp");
                driver.setLicenseImageUrl("/images/license-" + user.getId() + ".webp");
                driver.setStatus(DriverStatus.APPROVED);
                driver.setRatingAvg(new BigDecimal("5.00"));
                driver.setRatingCount(5);
                return driverRepository.save(driver);
        }

        private void realignPinnedIdentitySequences() {
                realignIdentitySequence("users");
                realignIdentitySequence("vehicle");
                realignIdentitySequence("booking");
                realignIdentitySequence("driver");
                realignIdentitySequence("renter");
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
                        entityManager.createNativeQuery(
                                        """
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
                        image.setImageUrl("/images/" + providerUserId + "/" + vehicle.getId() + "/"
                                        + encodePathSegment(file));
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
        createPlan(vehicle, PlanType.HOURLY, 240, prices.hourly(), UPFRONT_HOURLY, true);
        createPlan(vehicle, PlanType.DAILY, 1440, prices.daily(), UPFRONT_DAILY, false);
        createPlan(vehicle, PlanType.MONTHLY, 43200, prices.monthly(), UPFRONT_MONTHLY, false);
        createPlan(vehicle, PlanType.YEARLY, 525600, "280000000.00", "50000000.00", false);
    }

    private void createPlan(Vehicle vehicle, PlanType planType, int slotLengthMinutes,
                            String slotPrice, String upfrontAmount, boolean hourly) {
        RentalPlan plan = new RentalPlan();
        plan.setVehicle(vehicle);
        plan.setPlanType(planType);
        plan.setSlotLengthMinutes(slotLengthMinutes);
        plan.setSlotPrice(new BigDecimal(slotPrice));
        plan.setUpfrontAmount(upfrontAmount == null ? null : new BigDecimal(upfrontAmount));
        plan.setStatus(PlanStatus.ACTIVE);
        if (planType != PlanType.YEARLY) {
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
        rentalPlanRepository.save(plan);
    }

        private static String encodePathSegment(String segment) {
                return segment.replace(" ", "%20");
        }
    private void seedSampleBooking(Renter renter,
                                   User renterUser,
                                   Provider provider,
                                   Vehicle vehicle,
                                   String bookingCode,
                                   BookingStatus status,
                                   Instant pickupTime) {
        if (bookingRepository.existsByBookingCode(bookingCode)) {
            return;
        }
        RentalPlan daily = rentalPlanRepository
                .findByVehicle_IdAndStatusOrderByIdAsc(vehicle.getId(), PlanStatus.ACTIVE)
                .stream()
                .filter(plan -> plan.getPlanType() == PlanType.DAILY)
                .findFirst()
                .orElseThrow();

        Booking booking = new Booking();
        booking.setBookingCode(bookingCode);
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(daily);
        booking.setStatus(status);
        booking.setPickupAddress(provider.getDepotAddress());
        booking.setPickupTime(pickupTime);
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
    private void seedInProgressBooking(Renter renter, User renterUser, Provider provider, Vehicle vehicle) {
        if (bookingRepository.existsByBookingCode(IN_PROGRESS_BOOKING_CODE)) {
            return;
        }
        RentalPlan daily = rentalPlanRepository
                .findByVehicle_IdAndStatusOrderByIdAsc(vehicle.getId(), PlanStatus.ACTIVE)
                .stream()
                .filter(plan -> plan.getPlanType() == PlanType.DAILY)
                .findFirst()
                .orElseThrow();
        Instant pickup = Instant.parse("2026-11-15T01:00:00Z");
        BigDecimal deposit = daily.getSlotPrice()
                .multiply(provider.getDepositRatio())
                .setScale(2, RoundingMode.HALF_UP);

        Booking booking = new Booking();
        booking.setBookingCode(IN_PROGRESS_BOOKING_CODE);
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(daily);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setPickupAddress(provider.getDepotAddress());
        booking.setPickupTime(pickup);
        booking.setPassengerCount(2);
        booking.setTotalAmount(daily.getSlotPrice());
        booking.setActualStartAt(pickup.plus(Duration.ofMinutes(5)));
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

        BookingCharge rental = new BookingCharge();
        rental.setBooking(booking);
        rental.setChargeType(ChargeType.BASE_RENTAL);
        rental.setDescription("Thuê 1 slot");
        rental.setQuantity(1);
        rental.setUnitPrice(daily.getSlotPrice());
        rental.setAmount(daily.getSlotPrice());
        rental.setCreatedBy(renterUser);
        rental.setWaived(false);
        chargeRepository.save(rental);

        BookingCharge depositCharge = new BookingCharge();
        depositCharge.setBooking(booking);
        depositCharge.setChargeType(ChargeType.DEPOSIT);
        depositCharge.setDescription("Cọc giữ chỗ");
        depositCharge.setQuantity(null);
        depositCharge.setUnitPrice(daily.getSlotPrice());
        depositCharge.setAppliedRate(provider.getDepositRatio());
        depositCharge.setAmount(deposit);
        depositCharge.setCreatedBy(renterUser);
        depositCharge.setWaived(false);
        chargeRepository.save(depositCharge);

        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setCharge(depositCharge);
        payment.setType(PaymentType.DEPOSIT_PAY);
        payment.setAmount(deposit);
        payment.setDirection(PaymentDirection.IN);
        payment.setMethod(PaymentMethod.CARD);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(pickup.minus(Duration.ofHours(1)));
        paymentTransactionRepository.save(payment);

        scheduleService.book(
                vehicle,
                booking,
                pickup,
                pickup.plus(Duration.ofMinutes(daily.getSlotLengthMinutes())));
    }
}
