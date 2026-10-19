package com.fptufa26.ecomotion.booking;

import com.fptufa26.ecomotion.booking.dto.request.AddBookingChargeRequest;
import com.fptufa26.ecomotion.booking.dto.request.AssignDriverRequest;
import com.fptufa26.ecomotion.booking.dto.request.RejectBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverAvailabilityResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.exception.BookingAccessDeniedException;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.exception.DriverUnavailableException;
import com.fptufa26.ecomotion.booking.exception.InvalidBookingStateException;
import com.fptufa26.ecomotion.booking.exception.SettledChargeException;
import com.fptufa26.ecomotion.booking.exception.VehicleUnavailableException;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
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
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProviderBookingServiceTest {

    @Autowired
    private ProviderBookingService providerBookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingChargeRepository bookingChargeRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleScheduleRepository vehicleScheduleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private RenterRepository renterRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RentalPlanRepository rentalPlanRepository;

    private User providerUser1;
    private User providerUser2;
    private Provider provider1;
    private Provider provider2;
    private Renter renter;
    private Vehicle vehicle1;
    private Vehicle vehicle2;
    private RentalPlan dailyPlan;
    private RentalPlan yearlyPlan;
    private Driver driver1;
    private Driver driver2;
    private Booking pendingBooking;

    @BeforeEach
    void setupData() {
        providerUser1 = createUser("provider1@test.com", "0901111111", "Provider One", Role.PROVIDER);
        providerUser2 = createUser("provider2@test.com", "0902222222", "Provider Two", Role.PROVIDER);

        provider1 = createProvider(providerUser1, "Provider Hub 1");
        provider2 = createProvider(providerUser2, "Provider Hub 2");

        User renterUser = createUser("renter@test.com", "0903333333", "Renter One", Role.RENTER);
        renter = createRenter(renterUser);

        vehicle1 = createVehicle(provider1, "51H-111.11", "VinFast", "VF 8");
        vehicle2 = createVehicle(provider2, "51H-222.22", "Toyota", "Camry");

        dailyPlan = createPlan(vehicle1, PlanType.DAILY, 1440, new BigDecimal("1200000.00"));
        yearlyPlan = createPlan(vehicle1, PlanType.YEARLY, 525600, new BigDecimal("35000000.00"));

        User driverUser1 = createUser("driver1@test.com", "0904444441", "Driver One", Role.DRIVER);
        driver1 = createDriver(driverUser1, provider1, "B2-12345");

        User driverUser2 = createUser("driver2@test.com", "0904444442", "Driver Two", Role.DRIVER);
        driver2 = createDriver(driverUser2, provider2, "C-98765");

        pendingBooking = createBooking("BK-TEST-001", renter, vehicle1, dailyPlan, BookingStatus.PENDING,
                Instant.now().plus(2, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("Feature 1: Provider retrieves their booking details successfully")
    void providerRetrievesTheirBooking_Success() {
        ProviderBookingDetailResponse detail = providerBookingService.getBookingDetail(
                providerUser1.getId(), pendingBooking.getBookingCode());

        assertThat(detail).isNotNull();
        assertThat(detail.bookingCode()).isEqualTo("BK-TEST-001");
        assertThat(detail.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(detail.renter().fullName()).isEqualTo("Renter One");
        assertThat(detail.vehicle().brand()).isEqualTo("VinFast");
        assertThat(detail.rentalPlan().slotPrice()).isEqualByComparingTo(new BigDecimal("1200000.00"));
        assertThat(detail.timeline()).isNotEmpty();
    }

    @Test
    @DisplayName("Feature 1: Provider cannot access another Provider's booking -> throws Forbidden")
    void providerCannotAccessAnotherProvidersBooking_ThrowsForbidden() {
        assertThatThrownBy(() -> providerBookingService.getBookingDetail(
                providerUser2.getId(), pendingBooking.getBookingCode()))
                .isInstanceOf(BookingAccessDeniedException.class)
                .hasMessageContaining("không có quyền truy cập");
    }

    @Test
    @DisplayName("Feature 1: Non-existent booking -> throws NotFound")
    void nonExistentBooking_ThrowsNotFound() {
        assertThatThrownBy(() -> providerBookingService.getBookingDetail(
                providerUser1.getId(), "BK-NONEXISTENT"))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("Không tìm thấy đơn đặt xe");
    }

    @Test
    @DisplayName("Feature 2: Accept booking succeeds for PENDING booking")
    void bookingAcceptance_Success() {
        ProviderBookingDetailResponse updated = providerBookingService.acceptBooking(
                providerUser1.getId(), pendingBooking.getBookingCode());

        assertThat(updated.status()).isEqualTo(BookingStatus.ACCEPTED);

        Booking reloaded = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Feature 2: Accept booking fails when state is not PENDING")
    void bookingAcceptance_FailsForNonPendingState() {
        pendingBooking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(pendingBooking);

        assertThatThrownBy(() -> providerBookingService.acceptBooking(
                providerUser1.getId(), pendingBooking.getBookingCode()))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("Chỉ có thể chấp nhận đơn ở trạng thái PENDING");
    }

    @Test
    @DisplayName("Feature 2: Accept booking fails when vehicle has conflicting schedule")
    void bookingAcceptance_FailsWhenVehicleHasConflictingSchedule() {
        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle1);
        schedule.setType(ScheduleType.MAINTENANCE);
        schedule.setStartAt(pendingBooking.getPickupTime().minus(1, ChronoUnit.HOURS));
        schedule.setEndAt(pendingBooking.getPickupTime().plus(10, ChronoUnit.HOURS));
        schedule.setStatus(ScheduleStatus.ACTIVE);
        vehicleScheduleRepository.save(schedule);

        assertThatThrownBy(() -> providerBookingService.acceptBooking(
                providerUser1.getId(), pendingBooking.getBookingCode()))
                .isInstanceOf(VehicleUnavailableException.class)
                .hasMessageContaining("Xe đang bận lịch bảo trì");
    }

    @Test
    @DisplayName("Feature 3: Reject booking succeeds for PENDING booking")
    void bookingRejection_Success() {
        RejectBookingRequest req = new RejectBookingRequest(CancelReason.PROVIDER_NO_VEHICLE, "Không sắp xếp được xe");
        ProviderBookingDetailResponse updated = providerBookingService.rejectBooking(
                providerUser1.getId(), pendingBooking.getBookingCode(), req);

        assertThat(updated.status()).isEqualTo(BookingStatus.REJECTED);

        Booking reloaded = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(reloaded.getCancelReason()).isNull(); // Validates DB CHECK constraint ck_booking_not_cancelled_fields
    }

    @Test
    @DisplayName("Feature 3: Reject booking fails when state is not PENDING")
    void bookingRejection_FailsForNonPendingState() {
        pendingBooking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(pendingBooking);

        assertThatThrownBy(() -> providerBookingService.rejectBooking(
                providerUser1.getId(), pendingBooking.getBookingCode(), null))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("Chỉ có thể từ chối đơn ở trạng thái PENDING");
    }

    @Test
    @DisplayName("Feature 4 & 5: Driver assignment succeeds")
    void driverAssignment_Success() {
        AssignDriverRequest req = new AssignDriverRequest(driver1.getId());
        ProviderBookingDetailResponse updated = providerBookingService.assignDriver(
                providerUser1.getId(), pendingBooking.getBookingCode(), req);

        assertThat(updated.driver()).isNotNull();
        assertThat(updated.driver().id()).isEqualTo(driver1.getId());
        assertThat(updated.driver().fullName()).isEqualTo("Driver One");

        Booking reloaded = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertThat(reloaded.getDriver()).isNotNull();
        assertThat(reloaded.getDriver().getId()).isEqualTo(driver1.getId());
    }

    @Test
    @DisplayName("Feature 5: Busy driver cannot be assigned -> throws Conflict")
    void busyDriverCannotBeAssigned_ThrowsConflict() {
        // Create an overlapping booking assigned to driver1
        Booking busyBooking = createBooking("BK-BUSY-001", renter, vehicle1, dailyPlan, BookingStatus.ACCEPTED,
                pendingBooking.getPickupTime().plus(1, ChronoUnit.HOURS));
        busyBooking.setDriver(driver1);
        bookingRepository.save(busyBooking);

        AssignDriverRequest req = new AssignDriverRequest(driver1.getId());
        assertThatThrownBy(() -> providerBookingService.assignDriver(
                providerUser1.getId(), pendingBooking.getBookingCode(), req))
                .isInstanceOf(DriverUnavailableException.class)
                .hasMessageContaining("đang bận chuyến");
    }

    @Test
    @DisplayName("Feature 5: Driver from another provider cannot be assigned")
    void driverFromAnotherProviderCannotBeAssigned() {
        AssignDriverRequest req = new AssignDriverRequest(driver2.getId());
        assertThatThrownBy(() -> providerBookingService.assignDriver(
                providerUser1.getId(), pendingBooking.getBookingCode(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thuộc quản lý của bạn");
    }

    @Test
    @DisplayName("Feature 5: Self-drive yearly booking rejects driver assignment")
    void selfDriveBooking_YearlyPlan_RejectsDriverAssignment() {
        Booking yearlyBooking = createBooking("BK-YEARLY-001", renter, vehicle1, yearlyPlan, BookingStatus.PENDING,
                Instant.now().plus(5, ChronoUnit.DAYS));

        AssignDriverRequest req = new AssignDriverRequest(driver1.getId());
        assertThatThrownBy(() -> providerBookingService.assignDriver(
                providerUser1.getId(), yearlyBooking.getBookingCode(), req))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("tự lái");
    }

    @Test
    @DisplayName("Feature 4: Available drivers query returns available and flags busy drivers")
    void getAvailableDrivers_FiltersBusyDrivers() {
        // Driver 1 busy
        Booking busyBooking = createBooking("BK-BUSY-002", renter, vehicle1, dailyPlan, BookingStatus.CONFIRMED,
                pendingBooking.getPickupTime());
        busyBooking.setDriver(driver1);
        bookingRepository.save(busyBooking);

        List<DriverAvailabilityResponse> availableList = providerBookingService.getAvailableDrivers(
                providerUser1.getId(), pendingBooking.getBookingCode());

        assertThat(availableList).hasSize(1);
        DriverAvailabilityResponse d1Response = availableList.get(0);
        assertThat(d1Response.id()).isEqualTo(driver1.getId());
        assertThat(d1Response.available()).isFalse();
        assertThat(d1Response.conflictReason()).contains("BK-BUSY-002");
    }

    @Test
    @DisplayName("Feature 6: Manual charge is created and total amount updated")
    void manualCharge_CreatedSuccessfully() {
        BigDecimal initialTotal = pendingBooking.getTotalAmount();
        AddBookingChargeRequest req = new AddBookingChargeRequest(
                ChargeType.OUT_OF_RADIUS,
                "Phụ phí giao xe ngoài bán kính 20km",
                20,
                new BigDecimal("10000.00"),
                null,
                new BigDecimal("200000.00"),
                "https://example.com/receipt.jpg"
        );

        BookingChargeResponse chargeResp = providerBookingService.addManualCharge(
                providerUser1.getId(), pendingBooking.getBookingCode(), req);

        assertThat(chargeResp).isNotNull();
        assertThat(chargeResp.chargeType()).isEqualTo(ChargeType.OUT_OF_RADIUS);
        assertThat(chargeResp.amount()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(chargeResp.createdByName()).isEqualTo("Provider One");

        Booking reloaded = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo(initialTotal.add(new BigDecimal("200000.00")));
    }

    @Test
    @DisplayName("Feature 6: Invalid charge amount <= 0 is rejected")
    void manualCharge_InvalidAmount_ThrowsBadRequest() {
        AddBookingChargeRequest req = new AddBookingChargeRequest(
                ChargeType.WAITING,
                "Phụ phí chờ",
                30,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                null
        );

        assertThatThrownBy(() -> providerBookingService.addManualCharge(
                providerUser1.getId(), pendingBooking.getBookingCode(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số tiền phụ phí phải lớn hơn 0");
    }

    @Test
    @DisplayName("Feature 7: Remove manual charge succeeds and updates total")
    void removeManualCharge_Success() {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(pendingBooking);
        charge.setChargeType(ChargeType.WAITING);
        charge.setDescription("Chờ khách 30 phút");
        charge.setQuantity(30);
        charge.setUnitPrice(new BigDecimal("5000.00"));
        charge.setAmount(new BigDecimal("150000.00"));
        charge.setCreatedBy(providerUser1);
        bookingChargeRepository.save(charge);

        pendingBooking.setTotalAmount(pendingBooking.getTotalAmount().add(charge.getAmount()));
        bookingRepository.save(pendingBooking);

        providerBookingService.removeManualCharge(providerUser1.getId(), pendingBooking.getBookingCode(), charge.getId());

        assertThat(bookingChargeRepository.findById(charge.getId())).isEmpty();
        Booking reloaded = bookingRepository.findById(pendingBooking.getId()).orElseThrow();
        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1200000.00"));
    }

    @Test
    @DisplayName("Feature 7: Settled paid financial records cannot be deleted -> throws Conflict")
    void removeManualCharge_PaidFinancialRecordCannotBeDeleted_ThrowsConflict() {
        BookingCharge charge = new BookingCharge();
        charge.setBooking(pendingBooking);
        charge.setChargeType(ChargeType.OVERTIME);
        charge.setDescription("Quá giờ 1 tiếng");
        charge.setQuantity(1);
        charge.setUnitPrice(new BigDecimal("200000.00"));
        charge.setAmount(new BigDecimal("200000.00"));
        charge.setCreatedBy(providerUser1);
        bookingChargeRepository.save(charge);

        PaymentTransaction paidTx = new PaymentTransaction();
        paidTx.setBooking(pendingBooking);
        paidTx.setCharge(charge);
        paidTx.setType(PaymentType.SETTLEMENT);
        paidTx.setAmount(charge.getAmount());
        paidTx.setDirection(PaymentDirection.IN);
        paidTx.setMethod(PaymentMethod.BANK_TRANSFER);
        paidTx.setStatus(PaymentStatus.PAID);
        paidTx.setPaidAt(Instant.now());
        paymentTransactionRepository.save(paidTx);

        assertThatThrownBy(() -> providerBookingService.removeManualCharge(
                providerUser1.getId(), pendingBooking.getBookingCode(), charge.getId()))
                .isInstanceOf(SettledChargeException.class)
                .hasMessageContaining("Không thể xóa khoản phụ phí đã được khách thanh toán");
    }

    @Test
    @DisplayName("Feature O07: Existing queue query still works")
    void existingO07_GetProviderBookings_Works() {
        List<ProviderBookingResponse> queue = providerBookingService.getProviderBookings(
                providerUser1.getId(), BookingStatus.PENDING);

        assertThat(queue).isNotEmpty();
        assertThat(queue.get(0).bookingCode()).isEqualTo("BK-TEST-001");
    }

    // Helper builders
    private User createUser(String email, String phone, String name, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setFullName(name);
        user.setPasswordHash("EcoMotion@123");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEkycStatus(EkycStatus.VERIFIED);
        return userRepository.save(user);
    }

    private Provider createProvider(User user, String displayName) {
        Provider p = new Provider();
        p.setUser(user);
        p.setDisplayName(displayName);
        p.setProviderType(ProviderType.BUSINESS);
        p.setHotline("0287300001");
        p.setDepotAddress("12 Nguyen Hue, Q1, HCMC");
        p.setStatus(ProviderStatus.APPROVED);
        p.setCommissionRate(new BigDecimal("0.1000"));
        p.setDepositRatio(new BigDecimal("0.2000"));
        return providerRepository.save(p);
    }

    private Renter createRenter(User user) {
        Renter r = new Renter();
        r.setUser(user);
        r.setLicenseVerified(true);
        return renterRepository.save(r);
    }

    private Vehicle createVehicle(Provider provider, String plate, String brand, String model) {
        Vehicle v = new Vehicle();
        v.setProvider(provider);
        v.setLicensePlate(plate);
        v.setChassisNo("CHASSIS-" + plate);
        v.setEngineNo("ENGINE-" + plate);
        v.setBrand(brand);
        v.setModel(model);
        v.setManufactureYear(2024);
        v.setSeatCount(5);
        v.setTransmission(Transmission.AUTOMATIC);
        v.setColor("Trắng");
        v.setFuelType(FuelType.EV);
        v.setStatus(VehicleStatus.APPROVED);
        v.setMaintenanceBufferMinutes(30);
        return vehicleRepository.save(v);
    }

    private RentalPlan createPlan(Vehicle vehicle, PlanType type, int slotMinutes, BigDecimal price) {
        RentalPlan plan = new RentalPlan();
        plan.setVehicle(vehicle);
        plan.setPlanType(type);
        plan.setSlotLengthMinutes(slotMinutes);
        plan.setSlotPrice(price);
        if (type == PlanType.YEARLY) {
            plan.setUpfrontAmount(price);
        }
        plan.setStatus(PlanStatus.ACTIVE);
        return rentalPlanRepository.save(plan);
    }

    private Driver createDriver(User user, Provider provider, String licenseNo) {
        Driver d = new Driver();
        d.setUser(user);
        d.setProvider(provider);
        d.setLicenseNo(licenseNo);
        d.setLicenseClass("B2");
        d.setLicenseExpiry(LocalDate.now().plusYears(5));
        d.setPortraitUrl("https://example.com/portrait.jpg");
        d.setLicenseImageUrl("https://example.com/license.jpg");
        d.setStatus(DriverStatus.APPROVED);
        return driverRepository.save(d);
    }

    private Booking createBooking(String code, Renter renter, Vehicle vehicle, RentalPlan plan, BookingStatus status, Instant pickup) {
        Booking b = new Booking();
        b.setBookingCode(code);
        b.setRenter(renter);
        b.setVehicle(vehicle);
        b.setPlan(plan);
        b.setStatus(status);
        b.setPickupAddress("123 Nguyen Hue, Q1, HCMC");
        b.setPickupTime(pickup);
        b.setPassengerCount(2);
        b.setTotalAmount(plan.getSlotPrice() != null ? plan.getSlotPrice() : new BigDecimal("1000000.00"));
        return bookingRepository.save(b);
    }
}
