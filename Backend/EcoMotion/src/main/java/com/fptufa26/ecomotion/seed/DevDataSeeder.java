package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.common.crypto.DeterministicCipher;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import com.fptufa26.ecomotion.vehicle.enums.DocumentStatus;
import com.fptufa26.ecomotion.vehicle.enums.DocumentType;
import com.fptufa26.ecomotion.vehicle.repository.VehicleDocumentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
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
    private static final String ADDRESS_B = "45 Lê Lợi, Quận 3, TP. Hồ Chí Minh";
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
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final BookingRepository bookingRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final DisputeRepository disputeRepository;
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
        Provider providerA = seedProvider(providerUserA, "EcoMotion Depot A", ADDRESS_A, "02873000010");
        Provider providerB = seedProvider(providerUserB, "EcoMotion Depot B", ADDRESS_B, "02873000011");

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

        seedAdminDemonstrationData(providerA, providerB);

        realignPinnedIdentitySequences();
    }

    private void realignPinnedIdentitySequences() {
        realignIdentitySequence("users");
        realignIdentitySequence("vehicle");
        realignIdentitySequence("driver");
        realignIdentitySequence("booking");
        realignIdentitySequence("booking_charge");
        realignIdentitySequence("payment_transaction");
        realignIdentitySequence("dispute");
    }

    private void seedAdminDemonstrationData(Provider providerA, Provider providerB) {
        // 1. Pending Driver
        User driverUser = seedUser(6L, "driver.pending@ecomotion.vn", "0901000020", "Phạm Văn Tài", Role.DRIVER);
        if (driverRepository.findByUser_Id(driverUser.getId()).isEmpty()) {
            Driver driver = new Driver();
            driver.setUser(driverUser);
            driver.setProvider(providerA);
            driver.setLicenseNo("290123456789");
            driver.setLicenseClass("B2");
            driver.setLicenseExpiry(LocalDate.of(2028, 12, 31));
            driver.setPortraitUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400");
            driver.setLicenseImageUrl("https://images.unsplash.com/photo-1544717305-2782549b5136?w=400");
            driver.setStatus(DriverStatus.PENDING);
            driver.setRatingAvg(BigDecimal.ZERO);
            driver.setRatingCount(0);
            driverRepository.save(driver);
        }

        // 2. Pending Provider
        User providerUserC = seedUser(7L, "provider.pending@ecomotion.vn", "0901000030", "Công ty TNHH Vận tải Toàn Cầu", Role.PROVIDER);
        if (providerRepository.findByUser_Id(providerUserC.getId()).isEmpty()) {
            Provider providerC = new Provider();
            providerC.setUser(providerUserC);
            providerC.setProviderType(ProviderType.BUSINESS);
            providerC.setDisplayName("Global EcoTrans Depot");
            providerC.setHotline("02873009999");
            providerC.setDepotAddress("88 Xa Lộ Hà Nội, TP. Thủ Đức, TP. Hồ Chí Minh");
            providerC.setCommissionRate(new BigDecimal("0.1200"));
            providerC.setDepositRatio(new BigDecimal("0.3000"));
            providerC.setBankName("Vietcombank");
            providerC.setBankAccountNo("0071001234567");
            providerC.setBankAccountHolder("CTY TNHH VAN TAI TOAN CAU");
            providerC.setBusinessLicenseNo("0318999888");
            providerC.setTaxCode("0318999888");
            providerC.setStatus(ProviderStatus.PENDING);
            providerRepository.save(providerC);
        }

        // 3. User with PENDING eKYC
        User renterEkyc = seedUser(8L, "renter.pending@ecomotion.vn", "0901000040", "Lê Hoàng Long", Role.RENTER);
        renterEkyc.setEkycStatus(EkycStatus.PENDING);
        userRepository.save(renterEkyc);
        if (renterRepository.findByUser_Id(renterEkyc.getId()).isEmpty()) {
            Renter renter = new Renter();
            renter.setUser(renterEkyc);
            renter.setCccdNo(cipher.encrypt("079095012345"));
            renter.setDateOfBirth(LocalDate.of(1995, 8, 20));
            renter.setGender("Nam");
            renter.setAddress("Số 10 Ba Tháng Hai, Quận 10, TP. Hồ Chí Minh");
            renter.setCccdImageUrl("https://images.unsplash.com/photo-1544717305-2782549b5136?w=400");
            renter.setCccdImageBackUrl("https://images.unsplash.com/photo-1544717305-2782549b5136?w=400");
            renter.setPortraitUrl("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400");
            renterRepository.save(renter);
        }

        // 4. Vehicle 9: Pending approval
        if (vehicleRepository.findById(9L).isEmpty()) {
            seedVehicle(providerA.getId(), 9L, PROVIDER_A_USER_ID, "VinFast", "VF 7 Plus", 2024, 5, Transmission.AUTOMATIC,
                    "Xám", FuelType.EV, "51A-99999",
                    "0.00", 0, new PlanPrices("280000.00", "1800000.00", "24000000.00"),
                    List.of(
                            "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114343463-750x375.webp",
                            "VinFast-VF6-VF7-Bangalore-Electric-Cars_20250628_114403238-1024x576.webp"),
                    "Xe điện SUV hạng C VinFast VF 7 Plus thế hệ mới chờ duyệt.");
            Vehicle v9 = vehicleRepository.findById(9L).orElseThrow();
            v9.setStatus(VehicleStatus.PENDING);
            vehicleRepository.save(v9);

            VehicleDocument doc1 = new VehicleDocument();
            doc1.setVehicle(v9);
            doc1.setDocType(DocumentType.REGISTRATION);
            doc1.setDocNumber("51A-99999/CA-TPHCM");
            doc1.setExpiryDate(LocalDate.of(2029, 5, 20));
            doc1.setFileUrl("https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=400");
            doc1.setStatus(DocumentStatus.PENDING);
            vehicleDocumentRepository.save(doc1);

            VehicleDocument doc2 = new VehicleDocument();
            doc2.setVehicle(v9);
            doc2.setDocType(DocumentType.INSPECTION);
            doc2.setDocNumber("KD-5199999");
            doc2.setExpiryDate(LocalDate.of(2026, 12, 31));
            doc2.setFileUrl("https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=400");
            doc2.setStatus(DocumentStatus.PENDING);
            vehicleDocumentRepository.save(doc2);

            VehicleDocument doc3 = new VehicleDocument();
            doc3.setVehicle(v9);
            doc3.setDocType(DocumentType.INSURANCE);
            doc3.setDocNumber("BH-998877");
            doc3.setExpiryDate(LocalDate.of(2027, 5, 20));
            doc3.setFileUrl("https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=400");
            doc3.setStatus(DocumentStatus.PENDING);
            vehicleDocumentRepository.save(doc3);
        }

        // 5. Booking, Charges, Payments, Dispute
        if (bookingRepository.findByBookingCode("BK-ECO-001").isEmpty()) {
            Renter renter1 = renterRepository.findByUser_Id(1L).orElseGet(() -> renterRepository.findAll().get(0));
            Vehicle v1 = vehicleRepository.findById(1L).orElseThrow();
            RentalPlan plan1 = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(1L).get(0);

            Booking b1 = new Booking();
            b1.setBookingCode("BK-ECO-001");
            b1.setRenter(renter1);
            b1.setVehicle(v1);
            b1.setPlan(plan1);
            b1.setStatus(BookingStatus.CONFIRMED);
            b1.setPickupAddress("12 Ngô Đức Kế, Bến Nghé, Quận 1, TP. Hồ Chí Minh");
            b1.setPickupTime(Instant.now().plus(java.time.Duration.ofDays(1)));
            b1.setPassengerCount(2);
            b1.setTotalAmount(new BigDecimal("1800000.00"));
            Booking savedBooking1 = bookingRepository.save(b1);

            BookingCharge charge1 = new BookingCharge();
            charge1.setBooking(savedBooking1);
            charge1.setChargeType(ChargeType.BASE_RENTAL);
            charge1.setDescription("Giá thuê cơ bản gói giờ (10 giờ)");
            charge1.setQuantity(10);
            charge1.setUnitPrice(new BigDecimal("180000.00"));
            charge1.setAmount(new BigDecimal("1800000.00"));
            charge1.setCreatedBy(userRepository.findById(1L).orElseThrow());
            charge1.setWaived(false);
            bookingChargeRepository.save(charge1);

            BookingCharge charge2 = new BookingCharge();
            charge2.setBooking(savedBooking1);
            charge2.setChargeType(ChargeType.DEPOSIT);
            charge2.setDescription("Cọc giữ chỗ 30%");
            charge2.setQuantity(null);
            charge2.setUnitPrice(new BigDecimal("540000.00"));
            charge2.setAmount(new BigDecimal("540000.00"));
            charge2.setCreatedBy(userRepository.findById(1L).orElseThrow());
            charge2.setWaived(false);
            BookingCharge savedCharge2 = bookingChargeRepository.save(charge2);

            PaymentTransaction tx1 = new PaymentTransaction();
            tx1.setBooking(savedBooking1);
            tx1.setCharge(savedCharge2);
            tx1.setType(PaymentType.DEPOSIT_PAY);
            tx1.setAmount(new BigDecimal("540000.00"));
            tx1.setDirection(PaymentDirection.IN);
            tx1.setMethod(PaymentMethod.BANK_TRANSFER);
            tx1.setStatus(PaymentStatus.PAID);
            tx1.setGatewayRef("VNPAY-20261016-001");
            tx1.setPaidAt(Instant.now().minus(java.time.Duration.ofHours(5)));
            paymentTransactionRepository.save(tx1);

            Dispute dispute = new Dispute();
            dispute.setBooking(savedBooking1);
            dispute.setCharge(savedCharge2);
            dispute.setRaisedBy(userRepository.findById(1L).orElseThrow());
            dispute.setCategory(DisputeCategory.CHARGE);
            dispute.setStatus(DisputeStatus.OPEN);
            dispute.setResolution(null);
            disputeRepository.save(dispute);
        }
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

    private Provider seedProvider(User user, String displayName, String depotAddress, String hotline) {
        Provider existing = providerRepository.findByUser_Id(user.getId()).orElse(null);
        if (existing != null) {
            if (existing.getDepotAddress() == null || existing.getDepotAddress().isBlank()) {
                existing.setDepotAddress(depotAddress);
                if (existing.getHotline() == null || existing.getHotline().isBlank()) {
                    existing.setHotline(hotline);
                }
                return providerRepository.save(existing);
            }
            return existing;
        }

        Provider provider = new Provider();
        provider.setUser(user);
        provider.setProviderType(ProviderType.BUSINESS);
        provider.setDisplayName(displayName);
        provider.setHotline(hotline);
        provider.setDepotAddress(depotAddress);
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
                                (                                 :id, :providerId, :licensePlate, :chassisNo, :engineNo, :brand, :model,
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
