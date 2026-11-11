package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.service.RefundEventService;
import com.fptufa26.ecomotion.booking.service.BookingService;
import org.junit.jupiter.params.provider.CsvSource;
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
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.entity.PaymentEvidence;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentReceiptStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.service.PaymentReceiptService;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentIntegrityIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (!Boolean.getBoolean("tv5.payment.postgres")) return;
        String url = System.getProperty("tv5.postgres.url");
        if (url == null || !url.startsWith("jdbc:postgresql://127.0.0.1:") || !url.endsWith("/tv5_test"))
            throw new IllegalStateException("Dedicated local tv5_test database required");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "tv5_test");
        registry.add("spring.datasource.password", () -> "tv5-local-test-only");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PaymentRequestService payments;
    @Autowired PaymentReceiptService receipts;
    @Autowired PaymentRequestRepository requests;
    @Autowired PaymentTransactionRepository ledger;
    @Autowired BookingRepository bookings;
    @Autowired com.fptufa26.ecomotion.finance.repository.RefundRequestRepository refunds;
    @Autowired VehicleScheduleRepository schedules;
    @Autowired ScheduleService schedule;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired BookingService bookingService;
    @Autowired RefundEventService refundEvents;
    @Autowired com.fptufa26.ecomotion.finance.service.RefundService refundService;
    @Autowired com.fptufa26.ecomotion.finance.service.RefundReceiptService refundReceipts;
    @Autowired com.fptufa26.ecomotion.booking.service.BookingWorkflowService workflow;
    @Autowired com.fptufa26.ecomotion.booking.service.VehicleOperationalRiskService riskService;

    @Test void optionalMetadataSupportsDistinctPartialPaymentsAndIdempotentRetries() {
        var first=new ConfirmPaymentRequest(new BigDecimal("10000"),null,null,"optional-first-"+bookingId);
        payments.confirmPayment(requestId,providerId,first);
        payments.confirmPayment(requestId,providerId,first);
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
        payments.confirmPayment(requestId,providerId,new ConfirmPaymentRequest(new BigDecimal("10000"),null,null,"optional-second-"+bookingId));
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(2);
        assertThat(requests.findById(requestId).orElseThrow().getPaidAmount()).isEqualByComparingTo("20000");
    }

    @Test void cancellationPersistsRecipientAndOwnerCanUpdateOnlyOpenRefunds() {
        String code=legacyDeposit("DAILY",48);
        var account=new com.fptufa26.ecomotion.finance.dto.request.RefundAccountRequest("Vietcombank","NGUYEN VAN TEST","0123456789");
        bookingService.cancel(renterId,code,account);
        var refund=refundService.listRefundsForProvider(providerId).stream().filter(r -> r.bookingCode().equals(code)).findFirst().orElseThrow();
        assertThat(refund.recipientAccountHolder()).isEqualTo(account.accountHolder());
        assertThat(refund.recipientAccountNumber()).isEqualTo(account.accountNumber());
        String stored=tx.execute(s -> (String)em.createNativeQuery("select recipient_account_number from refund_request where id=?")
                .setParameter(1,refund.id()).getSingleResult());
        assertThat(stored).isNotEqualTo(account.accountNumber());
        assertThatThrownBy(() -> bookingService.updateRefundAccount(outsiderId,code,account))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.NotFoundException.class);
        var changed=new com.fptufa26.ecomotion.finance.dto.request.RefundAccountRequest("ACB","NGUYEN VAN TEST","9988776655");
        bookingService.updateRefundAccount(renterId,code,changed);
        assertThat(refundService.listRefundsForProvider(providerId).stream().filter(r -> r.id().equals(refund.id())).findFirst().orElseThrow().recipientAccountNumber()).isEqualTo("9988776655");
        tx.executeWithoutResult(s -> em.find(com.fptufa26.ecomotion.finance.entity.RefundRequest.class,refund.id()).setStatus(RefundStatus.REFUNDED));
        assertThatThrownBy(() -> bookingService.updateRefundAccount(renterId,code,account)).isInstanceOf(ConflictException.class);
    }

    @Test void cancelledBookingHasNoOperationalRiskEvenWithOlderActiveTrip() {
        String code=legacyDeposit("DAILY",48);
        tx.executeWithoutResult(s -> {
            Booking current=em.find(Booking.class,bookingId);
            Booking older=new Booking();older.setBookingCode("OLDER-"+bookingId);older.setVehicle(current.getVehicle());
            older.setRenter(current.getRenter());older.setPlan(current.getPlan());older.setStatus(BookingStatus.IN_PROGRESS);
            older.setPickupTime(Instant.now().minusSeconds(3600));older.setPickupAddress("Earlier pickup");
            older.setPassengerCount(1);older.setTotalAmount(new BigDecimal("100000"));older.setActualStartAt(Instant.now().minusSeconds(3600));em.persist(older);
        });
        assertThat(riskService.assessForRenter(renterId,code).atRisk()).isTrue();
        bookingService.cancel(renterId,code);
        assertThat(riskService.assessForRenter(renterId,code).atRisk()).isFalse();
    }

    @Test void operationalRiskDoesNotDiscloseAnotherRentersBooking() throws Exception {
        String code = legacyDeposit("DAILY", 48);
        String otherCode = "PRIVATE-" + bookingId;
        long otherRenterId = tx.execute(s -> {
            Booking current = em.find(Booking.class, bookingId);
            Renter otherRenter = new Renter(); otherRenter.setUser(user(Role.RENTER)); em.persist(otherRenter);
            Booking older = new Booking(); older.setBookingCode(otherCode); older.setVehicle(current.getVehicle());
            older.setRenter(otherRenter); older.setPlan(current.getPlan()); older.setStatus(BookingStatus.IN_PROGRESS);
            older.setPickupTime(Instant.now().minusSeconds(3600)); older.setPickupAddress("Private pickup");
            older.setPassengerCount(1); older.setTotalAmount(new BigDecimal("100000"));
            older.setActualStartAt(Instant.now().minusSeconds(3600)); em.persist(older);
            return otherRenter.getUser().getId();
        });
        String renterToken = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, renterId)));
        String otherToken = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, otherRenterId)));
        String providerToken = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, providerId)));
        String outsiderToken = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, outsiderId)));

        String risk = mvc.perform(get("/api/renter/bookings/{code}/operational-risk", code)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.atRisk").value(true))
                .andExpect(jsonPath("$.data.riskType").value("VEHICLE_BUSY"))
                .andExpect(jsonPath("$.data.blockingBookingCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(risk).doesNotContain(otherCode, "Private pickup", "customerId", "driverId", "bookingId");
        String list = mvc.perform(get("/api/renter/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("riskMessage").doesNotContain(otherCode, "Private pickup");
        String detail = mvc.perform(get("/api/renter/bookings/{code}", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(otherCode, "Private pickup");
        mvc.perform(get("/api/renter/bookings/{code}", otherCode).header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/renter/bookings/{code}/operational-risk", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/provider/bookings/{code}/operational-risk", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.atRisk").value(true));
        mvc.perform(get("/api/provider/bookings/{code}/operational-risk", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/provider/bookings/{code}/assign-candidates", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
        long driverUserId = tx.execute(s -> {
            var driver = new com.fptufa26.ecomotion.user.entity.Driver();
            driver.setUser(user(Role.DRIVER)); driver.setProvider(em.find(Booking.class, bookingId).getVehicle().getProvider());
            driver.setLicenseNo("LIC" + bookingId); driver.setLicenseClass("B2");
            driver.setLicenseExpiry(java.time.LocalDate.of(2040, 1, 1));
            driver.setPortraitUrl("portrait"); driver.setLicenseImageUrl("license");
            driver.setStatus(com.fptufa26.ecomotion.user.enums.DriverStatus.APPROVED); em.persist(driver);
            return driver.getUser().getId();
        });
        String driverToken = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, driverUserId)));
        mvc.perform(get("/api/driver/trips/{code}", code).header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    @Test void manualPaymentDoesNotRequireBankReferenceOrNote() {
        payments.confirmPayment(requestId, providerId, new ConfirmPaymentRequest(new BigDecimal("30000"), null, null));
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        PaymentTransaction payment=ledger.findByBooking_Id(bookingId).getFirst();
        assertThat(payment.getGatewayRef()).isNull();
        assertThat(payment.getProcessingNote()).isNull();
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test void cancellationRetryReturnsThePersistedCappedRefund() {
        String code=legacyDeposit("DAILY",48);
        tx.executeWithoutResult(s -> refundEvents.reserve(bookingId,"EXCESS:earlier",new BigDecimal("10000"),
                "Existing allocation",em.find(Booking.class,bookingId).getRenter().getUser()));
        var first=bookingService.cancel(renterId,code);
        var retry=bookingService.cancel(renterId,code);
        assertThat(first.refundAmount()).isEqualByComparingTo("20000");
        assertThat(retry.refundAmount()).isEqualByComparingTo(first.refundAmount());
    }

    @Test void fullyRefundedLegacyDepositDoesNotRemainInCancellationReview() {
        String code=legacyDeposit("DAILY",48);
        tx.executeWithoutResult(s -> {
            var allocated=refundEvents.reserve(bookingId,"EXCESS:all",new BigDecimal("30000"),
                    "Already returned",em.find(Booking.class,bookingId).getRenter().getUser());
            allocated.forEach(r -> {r.setStatus(RefundStatus.REFUNDED);
                r.getPaymentTransaction().setStatus(PaymentStatus.PAID);});
        });
        assertThat(bookingService.cancel(renterId,code).status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void unallocatedReceivedMoneyStillRequiresReconciliationWhenRequestIsMissing() {
        String code=legacyDeposit("DAILY",48);
        tx.executeWithoutResult(s -> ledger.findByBooking_Id(bookingId).forEach(t -> {
            t.setAppliedAmount(BigDecimal.ZERO);t.setProcessingNote("RECONCILIATION_REQUIRED: legacy receipt");
        }));
        assertThat(bookingService.cancel(renterId,code).status()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
    }

    private String legacyDeposit(String planType, long leadHours) {
        return tx.execute(s -> {
            Booking b = em.find(Booking.class, bookingId);
            b.setStatus(BookingStatus.CONFIRMED);
            b.setPickupTime(Instant.now().plusSeconds(leadHours * 3600));
            BookingPolicySnapshot snapshot = em.createQuery("select s from BookingPolicySnapshot s where s.booking.id=:id", BookingPolicySnapshot.class)
                    .setParameter("id", bookingId).getSingleResult();
            snapshot.setPlanType(PlanType.valueOf(planType));
            em.remove(em.find(PaymentRequest.class, requestId));
            PaymentTransaction payment = new PaymentTransaction(); payment.setBooking(b);
            payment.setType(PaymentType.DEPOSIT_PAY); payment.setDirection(PaymentDirection.IN);
            payment.setStatus(PaymentStatus.PAID); payment.setMethod(PaymentMethod.BANK_TRANSFER);
            payment.setAmount(new BigDecimal("30000")); payment.setAppliedAmount(new BigDecimal("30000"));
            payment.setPaidAt(Instant.now()); em.persist(payment);
            return b.getBookingCode();
        });
    }

    @ParameterizedTest
    @CsvSource({"DAILY,48,30000", "DAILY,2,15000", "MONTHLY,744,30000", "MONTHLY,24,15000", "YEARLY,48,0"})
    void cancellationUsesRecordedDepositWithoutPaymentRequest(String plan, long leadHours, String expected) {
        String code = legacyDeposit(plan, leadHours);
        bookingService.cancel(renterId, code);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(new BigDecimal(expected).signum() == 0 ? BookingStatus.CANCELLED : BookingStatus.CANCELLATION_REVIEW);
        tx.executeWithoutResult(s -> {
            var refunds = em.createQuery("select r from RefundRequest r where r.booking.id=:id", RefundRequest.class)
                    .setParameter("id", bookingId).getResultList();
            assertThat(refunds.stream().map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(expected);
        });
        bookingService.cancel(renterId, code);
    }

    TransactionTemplate tx;
    long bookingId;
    long requestId;
    long providerId;
    long renterId;
    long outsiderId;
    String requestCode;

    private User user(Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@payment-integrity.test");
        user.setPasswordHash("fixture-only"); user.setFullName("Payment test");
        user.setRole(role); user.setStatus(UserStatus.ACTIVE); em.persist(user);
        return com.fptufa26.ecomotion.support.IdentityTestFixtures.verify(em, user);
    }

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            Provider provider = new Provider(); provider.setUser(user(Role.PROVIDER));
            provider.setProviderType(ProviderType.INDIVIDUAL); provider.setStatus(ProviderStatus.APPROVED);
            provider.setDisplayName("Payment test"); provider.setHotline("0900000000"); provider.setDepotAddress("Test");
            provider.setCommissionRate(new BigDecimal("0.1")); provider.setDepositRatio(new BigDecimal("0.3"));
            em.persist(provider); providerId = provider.getUser().getId();
            Renter renter = new Renter(); renter.setUser(user(Role.RENTER)); em.persist(renter); renterId = renter.getUser().getId();
            outsiderId = user(Role.PROVIDER).getId();
            Provider other = new Provider(); other.setUser(em.find(User.class, outsiderId));
            other.setProviderType(ProviderType.INDIVIDUAL); other.setStatus(ProviderStatus.APPROVED);
            other.setDisplayName("Other provider"); other.setHotline("0900000001"); other.setDepotAddress("Other depot");
            other.setCommissionRate(new BigDecimal("0.1")); other.setDepositRatio(new BigDecimal("0.3")); em.persist(other);
            ProviderBankAccount bank = new ProviderBankAccount(); bank.setProvider(provider); bank.setBankCode("VCB");
            bank.setBankName("Test bank"); bank.setAccountNumber("0123456789"); bank.setAccountHolderName("TEST OWNER");
            bank.setStatus(BankAccountStatus.ACTIVE); em.persist(bank);
            Vehicle vehicle = new Vehicle(); vehicle.setProvider(provider); vehicle.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            vehicle.setChassisNo(UUID.randomUUID().toString()); vehicle.setEngineNo(UUID.randomUUID().toString());
            vehicle.setBrand("Test"); vehicle.setModel("Car"); vehicle.setColor("White"); vehicle.setManufactureYear(2026);
            vehicle.setSeatCount(4); vehicle.setFuelType(FuelType.EV); vehicle.setTransmission(Transmission.AUTOMATIC);
            vehicle.setStatus(VehicleStatus.APPROVED); vehicle.setMaintenanceBufferMinutes(0); em.persist(vehicle);
            RentalPlan plan = new RentalPlan(); plan.setVehicle(vehicle); plan.setPlanType(PlanType.HOURLY);
            plan.setSlotLengthMinutes(60); plan.setSlotPrice(new BigDecimal("100000")); em.persist(plan);
            Booking booking = new Booking(); booking.setBookingCode(UUID.randomUUID().toString().substring(0, 30).toUpperCase(java.util.Locale.ROOT));
            booking.setRenter(renter); booking.setVehicle(vehicle); booking.setPlan(plan); booking.setPickupTime(Instant.now().plusSeconds(86400));
            booking.setPickupAddress("Test pickup"); booking.setPassengerCount(1); booking.setTotalAmount(new BigDecimal("100000"));
            booking.setStatus(BookingStatus.AWAITING_DEPOSIT); em.persist(booking); bookingId = booking.getId();
            BookingPolicySnapshot snapshot = new BookingPolicySnapshot(); snapshot.setBooking(booking); snapshot.setPlanType(PlanType.HOURLY);
            snapshot.setSlotLengthMinutes(60); snapshot.setSlotPrice(plan.getSlotPrice()); snapshot.setDepositRatio(new BigDecimal("0.3"));
            snapshot.setDepositAmount(new BigDecimal("30000")); em.persist(snapshot);
            BookingCharge charge = new BookingCharge(); charge.setBooking(booking); charge.setChargeType(ChargeType.BASE_RENTAL);
            charge.setQuantity(1); charge.setUnitPrice(plan.getSlotPrice()); charge.setAmount(plan.getSlotPrice());
            charge.setDescription("Base rental fixture"); charge.setCreatedBy(provider.getUser()); em.persist(charge);
            schedule.hold(vehicle, booking, booking.getPickupTime(), booking.getPickupTime().plusSeconds(3600), Instant.now().plusSeconds(900));
            PaymentRequest request = payments.createDepositPaymentRequest(booking);
            requestId = request.getId(); requestCode = request.getPaymentCode();
        });
    }

    private void confirm(String amount, String reference) {
        payments.confirmPayment(requestId, providerId, new ConfirmPaymentRequest(new BigDecimal(amount), reference, "Received transfer"));
    }

    private void assertReconciliation(BookingStatus bookingStatus) {
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(bookingStatus);
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.REVIEW_REQUIRED);
        PaymentTransaction payment = ledger.findByBooking_Id(bookingId).getFirst();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmount()).isPositive();
        assertThat(payment.getAppliedAmount()).isZero();
        assertThat(payment.getProcessingNote()).startsWith("RECONCILIATION_REQUIRED");
        assertThat(receipts.getReceipt(renterId, payment.getTransactionCode()).status()).isEqualTo(PaymentReceiptStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    void validHoldConfirmsAndReceiptUsesPersistedPayment() {
        confirm("30000", "VALID-" + requestId);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(schedules.findByBooking_Id(bookingId)).allMatch(s -> s.getStatus() == ScheduleStatus.ACTIVE);
        var receipt = receipts.getReceipt(renterId, requestCode);
        assertThat(receipt.status()).isEqualTo(PaymentReceiptStatus.SUCCESS);
        assertThat(receipt.amount()).isEqualByComparingTo("30000");
        assertThat(receipt.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(receipt.transactionCode()).isNotEqualTo(requestCode);
    }

    @Test
    void confirmingDepositOnCancellationReviewRecordsMoneyAndReservesFullRefund() {
        tx.executeWithoutResult(s -> {
            em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW);
            em.find(PaymentRequest.class, requestId).setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
            schedule.release(bookingId);
        });

        confirm("30000", "CANCELLED-RECEIPT-" + requestId);

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        PaymentRequest request = requests.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(request.getPaidAmount()).isEqualByComparingTo("30000");
        PaymentTransaction payment = ledger.findByBooking_Id(bookingId).getFirst();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAppliedAmount()).isZero();
        List<RefundRequest> refunds = tx.execute(s -> em.createQuery(
                        "select r from RefundRequest r where r.booking.id=:id", RefundRequest.class)
                .setParameter("id", bookingId).getResultList());
        assertThat(refunds).singleElement().satisfies(refund -> {
            assertThat(refund.getAmount()).isEqualByComparingTo("30000");
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
        });
    }

    @Test
    void cancellationReceiptRefundIsCalculatedFromEachRemainingReceipt() {
        tx.executeWithoutResult(s -> {
            em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW);
            em.find(PaymentRequest.class, requestId).setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
            schedule.release(bookingId);
        });

        confirm("10000", "PARTIAL-1-" + requestId);
        confirm("20000", "PARTIAL-2-" + requestId);

        List<RefundRequest> refunds = tx.execute(s -> em.createQuery(
                        "select r from RefundRequest r where r.booking.id=:id order by r.id", RefundRequest.class)
                .setParameter("id", bookingId).getResultList());
        assertThat(refunds).hasSize(2);
        assertThat(refunds).extracting(RefundRequest::getAmount)
                .containsExactly(new BigDecimal("10000.00"), new BigDecimal("20000.00"));
        assertThat(refunds.stream().map(RefundRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("30000");
        assertThat(refunds).allMatch(refund -> refund.getStatus() == RefundStatus.PROCESSING);
        assertThat(ledger.findByBooking_Id(bookingId).stream()
                .filter(t -> t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAppliedAmount))
                .allMatch(applied -> applied != null && applied.signum() == 0);
    }

    @Test
    void cancellationReceiptRefundRequiresRenterAccountBeforeProviderCanConfirmTransfer() {
        tx.executeWithoutResult(s -> {
            em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW);
            em.find(PaymentRequest.class, requestId).setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
            schedule.release(bookingId);
        });
        confirm("30000", "CANCELLED-ACCOUNT-" + requestId);
        Long refundId = tx.execute(s -> em.createQuery(
                        "select r.id from RefundRequest r where r.booking.id=:id", Long.class)
                .setParameter("id", bookingId).getSingleResult());
        Long outId = tx.execute(s -> em.find(RefundRequest.class, refundId).getPaymentTransaction().getId());
        var proof = new org.springframework.mock.web.MockMultipartFile(
                "file", "refund.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

        assertThatThrownBy(() -> refundService.completeRefund(providerId, outId, proof, "ACCOUNT-" + requestId, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tài khoản nhận hoàn");

        String code = bookings.findById(bookingId).orElseThrow().getBookingCode();
        bookingService.updateRefundAccount(renterId, code,
                new com.fptufa26.ecomotion.finance.dto.request.RefundAccountRequest(
                        "Vietcombank", "NGUYEN VAN TEST", "0123456789"));

        assertThat(refundService.completeRefund(providerId, outId, proof, "ACCOUNT-" + requestId, null).status())
                .isEqualTo(PaymentStatus.PAID);
        RefundStatus resolved = tx.execute(s -> em.find(RefundRequest.class, refundId).getStatus());
        assertThat(resolved).isEqualTo(RefundStatus.REFUNDED);

        // The receipt was recorded with appliedAmount 0 because the booking was already cancelled.
        // Once the refund is reserved and completed, that must not keep the workflow stuck in
        // RECONCILIATION_REQUIRED.
        assertThat(workflow.getWorkflow(renterId, code).paymentStatus()).isEqualTo("REFUNDED");
        refundReceipts.respond(renterId, refundId, true);
        var afterReceipt = workflow.getWorkflow(renterId, code);
        assertThat(afterReceipt.paymentStatus()).isEqualTo("REFUNDED");
        assertThat(afterReceipt.refunds()).singleElement()
                .extracting(com.fptufa26.ecomotion.finance.dto.response.RefundResponse::receiptStatus)
                .isEqualTo("CONFIRMED");
    }

    @Test
    void expiredHoldRecordsMoneyWithoutActivatingSchedule() {        tx.executeWithoutResult(s -> schedules.findByBooking_Id(bookingId).forEach(h -> h.setExpiresAt(Instant.now().minusSeconds(1))));
        confirm("30000", "EXPIRED-" + requestId);
        assertReconciliation(BookingStatus.CANCELLATION_REVIEW);
        assertThat(schedules.findByBooking_Id(bookingId)).noneMatch(h -> h.getStatus() == ScheduleStatus.ACTIVE);
    }

    @Test
    void competingScheduleRecordsMoneyWithoutConfirmingBooking() {
        tx.executeWithoutResult(s -> {
            Booking booking = em.find(Booking.class, bookingId);
            VehicleSchedule block = new VehicleSchedule(); block.setVehicle(booking.getVehicle());
            block.setType(ScheduleType.BLOCKED); block.setStatus(ScheduleStatus.ACTIVE);
            block.setStartAt(booking.getPickupTime()); block.setEndAt(booking.getPickupTime().plusSeconds(3600));
            em.persist(block);
        });
        confirm("30000", "CONFLICT-" + requestId);
        assertReconciliation(BookingStatus.CANCELLATION_REVIEW);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "REJECTED"})
    void terminalBookingRejectsNewManualConfirmationWithoutResurrection(BookingStatus status) {
        tx.executeWithoutResult(s -> {
            em.find(Booking.class, bookingId).setStatus(status);
            em.find(PaymentRequest.class, requestId).setStatus(PaymentRequestStatus.VOIDED);
            schedule.release(bookingId);
        });
        assertThatThrownBy(() -> confirm("30000", "TERMINAL-" + requestId))
                .isInstanceOf(status == BookingStatus.REJECTED
                        ? InvalidPaymentOperationException.class : ConflictException.class);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentRequestStatus.class, names = {"PENDING", "REVIEW_REQUIRED"})
    void disputedFinalPaymentCannotCompleteBooking(PaymentRequestStatus requestStatus) {
        tx.executeWithoutResult(s -> {
            Booking booking = em.find(Booking.class, bookingId);
            booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT); booking.setActualEndAt(Instant.now());
            booking.setProviderConfirmedAt(Instant.now()); booking.setRenterConfirmedAt(Instant.now());
            PaymentRequest request = em.find(PaymentRequest.class, requestId);
            request.setPaymentType(PaymentRequestType.FINAL_BALANCE); request.setStatus(requestStatus);
            Dispute dispute = new Dispute(); dispute.setBooking(booking); dispute.setRaisedBy(booking.getRenter().getUser());
            dispute.setCategory(DisputeCategory.SERVICE_QUALITY); dispute.setStatus(DisputeStatus.OPEN); em.persist(dispute);
        });
        confirm("30000", "DISPUTE-" + requestId);
        assertReconciliation(BookingStatus.AWAITING_FINAL_PAYMENT);
    }

    @Test
    void partialDepositKeepsPayButtonForRemainingAndRenterCanAcknowledge() {
        confirm("20000", "PARTIAL-" + requestId);
        String code = tx.execute(s -> em.find(Booking.class, bookingId).getBookingCode());
        var detail = bookingService.detail(renterId, code);
        assertThat(detail.canPay()).as("còn thiếu 10.000 đ thì khách vẫn thanh toán được").isTrue();
        var request = requests.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PARTIALLY_PAID);

        var acknowledged = payments.acknowledgeReceipt(requestId, renterId);
        assertThat(acknowledged.renterAcknowledgedAt()).isNotNull();
        assertThatThrownBy(() -> payments.acknowledgeReceipt(requestId, providerId)).isInstanceOf(AccessDeniedException.class);

        // Nhà xe ghi nhận thêm tiền: khách phải đối chiếu lại.
        confirm("10000", "PARTIAL-REST-" + requestId);
        assertThat(requests.findById(requestId).orElseThrow().getRenterAcknowledgedAt()).isNull();
        assertThat(bookingService.detail(renterId, code).canPay()).isFalse();
    }

    @Test
    void depositOverpaymentRecordsFullAmountAndCreatesMandatoryRefund() {
        confirm("50000", "OVER-" + requestId);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        var paid = ledger.findByBooking_Id(bookingId).stream()
                .filter(t -> t.getDirection() == com.fptufa26.ecomotion.booking.enums.PaymentDirection.IN).toList();
        assertThat(paid).singleElement().satisfies(t -> {
            assertThat(t.getAmount()).isEqualByComparingTo("50000");
            assertThat(t.getAppliedAmount()).isEqualByComparingTo("30000");
        });
        var excess = refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        assertThat(excess).singleElement().satisfies(r -> {
            assertThat(r.getAmount()).isEqualByComparingTo("20000");
            assertThat(r.getStatus()).isEqualTo(com.fptufa26.ecomotion.finance.enums.RefundStatus.PROCESSING);
        });
        assertThat(refundEvents.hasOpenExcessRefunds(bookingId)).isTrue();

        // Khách chọn cấn trừ: lệnh hoàn bị huỷ, phần dư thành tiền đã trả cho đơn.
        var offset = refundService.offsetExcessRefund(renterId, excess.get(0).getId());
        assertThat(offset.status()).isEqualTo(com.fptufa26.ecomotion.finance.enums.RefundStatus.REJECTED);
        assertThat(offset.excess()).isTrue();
        assertThat(refundEvents.hasOpenExcessRefunds(bookingId)).isFalse();
        assertThat(ledger.findById(paid.get(0).getId()).orElseThrow().getAppliedAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void finalOverpaymentHoldsCompletionUntilExcessIsRefunded() {
        tx.executeWithoutResult(s -> {
            Booking booking = em.find(Booking.class, bookingId);
            booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT); booking.setActualEndAt(Instant.now());
            booking.setProviderConfirmedAt(Instant.now()); booking.setRenterConfirmedAt(Instant.now());
            em.find(PaymentRequest.class, requestId).setPaymentType(PaymentRequestType.FINAL_BALANCE);
            em.find(PaymentRequest.class, requestId).setAmount(new BigDecimal("100000"));
        });
        confirm("120000", "FINAL-OVER-" + requestId);
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .as("còn 20.000 đ chuyển dư chưa hoàn thì chưa hoàn tất").isEqualTo(BookingStatus.AWAITING_FINAL_PAYMENT);
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).singleElement()
                .satisfies(r -> assertThat(r.getAmount()).isEqualByComparingTo("20000"));
        assertThatThrownBy(() -> refundService.offsetExcessRefund(renterId,
                refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId).get(0).getId()))
                .as("đã tới quyết toán thì phải hoàn, không cấn trừ").isInstanceOf(ConflictException.class);
    }

    @Test
    void renterCanMarkTransferSentWithoutReceiptImage() {
        payments.submitPaymentProof(requestId, renterId, null, null, null, null, null);
        var request = requests.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PROOF_SUBMITTED);
    }

    @Test
    void invalidAmountAndWrongProviderDoNotRecordMoney() {
        assertThatThrownBy(() -> confirm("0", "INVALID")).isInstanceOf(InvalidPaymentOperationException.class);
        assertThatThrownBy(() -> confirm("-1", "NEGATIVE")).isInstanceOf(InvalidPaymentOperationException.class);
        assertThatThrownBy(() -> payments.confirmPayment(requestId, outsiderId,
                new ConfirmPaymentRequest(new BigDecimal("30000"), "OTHER", null))).isInstanceOf(AccessDeniedException.class);
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @Test
    void providerRejectsManualRequestWithReasonWithoutRecordingPayment() {
        tx.executeWithoutResult(s -> {
            PaymentRequest pr = em.find(PaymentRequest.class, requestId);
            pr.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
            PaymentEvidence evidence = new PaymentEvidence();
            evidence.setPaymentRequest(pr);
            evidence.setStatus(PaymentEvidenceStatus.PENDING);
            evidence.setProofImageUrl("test.jpg");
            evidence.setTransferredAmount(new BigDecimal("30000"));
            evidence.setCreatedAt(Instant.now());
            evidence.setSubmittedBy(pr.getBooking().getRenter().getUser());
            em.persist(evidence);
            pr.getEvidences().add(evidence);
        });
        assertThatThrownBy(() -> payments.rejectPayment(requestId, providerId,
                new com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest("   ")))
                .isInstanceOf(InvalidPaymentOperationException.class);
        var result = payments.rejectPayment(requestId, providerId,
                new com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest("Không tìm thấy giao dịch"));
        assertThat(result.status()).isEqualTo(PaymentRequestStatus.REJECTED);
        assertThat(result.rejectionReason()).isEqualTo("Không tìm thấy giao dịch");
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
    }

    @Test
    void partialPaymentsGetDistinctCodesAndOnlyCompleteAfterFullAmount() {
        confirm("10000", "PARTIAL-1-" + requestId);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        confirm("20000", "PARTIAL-2-" + requestId);
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(2).extracting(PaymentTransaction::getTransactionCode).doesNotHaveDuplicates();
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    private String reconciliationBody(String amount, String bankRef, String key) {
        return """
                {"receivedAmount":%s,"bankTransactionRef":"%s","bankStatementReference":"STATEMENT-2026-11-08",
                 "bankReceiptVerified":true,"reconciliationId":"%s","receivedAt":"%s","note":"Verified credited bank statement"}
                """.formatted(amount, bankRef, key, Instant.now().minusSeconds(60));
    }

    private org.springframework.test.web.servlet.ResultActions reconcileHttp(long actor, String body) throws Exception {
        return reconcileHttpAt(requestId, actor, body);
    }

    private org.springframework.test.web.servlet.ResultActions reconcileHttpAt(long targetId, long actor, String body) throws Exception {
        String token = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, actor)));
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/provider/payment-requests/" + targetId + "/bank-reconciliation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType("application/json").content(body));
    }

    @ParameterizedTest
    @CsvSource({"10000", "40000"})
    void verifiedWrongAmountIsRetainedUnappliedWithoutAutomaticRefund(String amount) throws Exception {
        String body = reconciliationBody(amount, "BANK-" + requestId, "RECON-" + requestId);
        reconcileHttp(providerId, body).andExpect(status().isOk());
        reconcileHttp(providerId, body).andExpect(status().isOk());
        assertReconciliation(BookingStatus.CANCELLATION_REVIEW);
        var row = ledger.findByBooking_Id(bookingId).getFirst();
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
        assertThat(row.getAmount()).isEqualByComparingTo(amount);
        assertThat(row.getAppliedAmount()).isZero();
        assertThat(row.getGatewayRef()).isEqualTo("BANK-" + requestId);
        assertThat(row.getProcessingNote()).contains("STATEMENT-2026-11-08");
        assertThat(requests.findById(requestId).orElseThrow().getPaidAmount()).isZero();
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
        assertThat(schedules.findByBooking_Id(bookingId)).noneMatch(h -> h.getStatus() == ScheduleStatus.ACTIVE);
        assertThatThrownBy(() -> confirm("30000", "BYPASS-" + requestId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void reconciliationRejectsChangedReplayAndDuplicateBankReference() throws Exception {
        String body = reconciliationBody("40000", "SAME-" + requestId, "KEY-" + requestId);
        reconcileHttp(providerId, body).andExpect(status().isOk());
        reconcileHttp(providerId, body.replace("40000", "41000")).andExpect(status().isConflict());
        reconcileHttp(providerId, body.replace("KEY-", "NEW-KEY-")).andExpect(status().isConflict());
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
    }

    @Test
    void bankReconciliationRequiresOwnerAndVerifiedStatementEvidence() throws Exception {
        String body = reconciliationBody("40000", "AUTH-" + requestId, "AUTH-" + requestId);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/provider/payment-requests/" + requestId + "/bank-reconciliation")
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        reconcileHttp(renterId, body).andExpect(status().isForbidden());
        reconcileHttp(outsiderId, body).andExpect(status().isForbidden());
        reconcileHttp(providerId, body.replace("true", "false")).andExpect(status().isBadRequest());
        reconcileHttp(providerId, body.replace("STATEMENT-2026-11-08", " ")).andExpect(status().isBadRequest());
        reconcileHttp(providerId, body.replace("AUTH-" + requestId, " ")).andExpect(status().isBadRequest());
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "1.001", "1000000000000"})
    void bankReconciliationRejectsInvalidAmounts(String amount) throws Exception {
        reconcileHttp(providerId, reconciliationBody(amount, "BAD-" + requestId, "BAD-" + requestId))
                .andExpect(status().isBadRequest());
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @Test
    void verifiedLateTransferReopensCancelledFinancialReviewWithoutResurrectingBooking() throws Exception {
        tx.executeWithoutResult(s -> {
            em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLED);
            em.find(PaymentRequest.class, requestId).setStatus(PaymentRequestStatus.VOIDED);
            schedule.release(bookingId);
        });
        reconcileHttp(providerId, reconciliationBody("40000", "LATE-" + requestId, "LATE-" + requestId))
                .andExpect(status().isOk());
        assertReconciliation(BookingStatus.CANCELLATION_REVIEW);
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
        assertThat(schedules.findByBooking_Id(bookingId)).noneMatch(h -> h.getStatus() == ScheduleStatus.ACTIVE);
    }

    @Autowired com.fptufa26.ecomotion.finance.service.CancellationClosureService closure;
    @Autowired com.fptufa26.ecomotion.finance.service.FinancialAuditService financialAudit;

    @Test
    void bankReceiptReviewClosesOnlyAfterExplicitFullManualRefund() throws Exception {
        reconcileHttp(providerId, reconciliationBody("40000", "CLOSE-" + requestId, "CLOSE-" + requestId))
                .andExpect(status().isOk());
        assertThat(closure.reconcile(bookingId)).isFalse();
        assertThat(financialAudit.history("BANK_RECEIPT", requestId)).singleElement().satisfies(a -> {
            assertThat(a.actorId()).isEqualTo(providerId);
            assertThat(a.payload()).contains("40000", "STATEMENT-2026-11-08", "Verified credited bank statement");
        });
        String code = bookings.findById(bookingId).orElseThrow().getBookingCode();
        var refund = refundService.createRefundRequest(providerId,
                new com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest(code, new BigDecimal("40000"),
                        "Manual decision to return entire bank exception", "statement-ref"));
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
        assertThat(closure.reconcile(bookingId)).isFalse();
        refundService.reviewRefundRequest(providerId, refund.id(),
                new com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest("APPROVE", null));
        long outId = tx.execute(s -> refunds.findById(refund.id()).orElseThrow().getPaymentTransaction().getId());
        bookingService.updateRefundAccount(renterId, code,
                new com.fptufa26.ecomotion.finance.dto.request.RefundAccountRequest("Vietcombank", "NGUYEN VAN TEST", "0123456789"));
        refundService.completeRefund(providerId, outId,
                new org.springframework.mock.web.MockMultipartFile("file", "bank.pdf", "application/pdf", "%PDF-1.4 qa".getBytes()),
                "RETURN-" + requestId, "Operator verified returned funds");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.VOIDED);
        assertThat(closure.reconcile(bookingId)).isTrue();
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).hasSize(1);
        assertThat(ledger.findByBooking_Id(bookingId)).filteredOn(t -> t.getDirection() == PaymentDirection.IN)
                .singleElement().satisfies(t -> assertThat(t.getAmount()).isEqualByComparingTo("40000"));
    }

    @Test
    void concurrentBankReceiptRetriesPersistOneReceiptAndOneAuditRecord() throws Exception {
        String body = reconciliationBody("40000", "RACE-" + requestId, "RACE-" + requestId);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return reconcileHttp(providerId, body).andReturn().getResponse().getStatus(); });
            var two = pool.submit(() -> { start.await(); return reconcileHttp(providerId, body).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactly(200, 200);
        }
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
        assertThat(financialAudit.history("BANK_RECEIPT", requestId)).hasSize(1);
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
    }

    @Test
    void sameBankReceiptCannotBeAssignedToTwoBookingsConcurrently() throws Exception {
        long secondRequest = tx.execute(s -> {
            var original = em.find(Booking.class, bookingId);
            var other = new Booking(); other.setBookingCode("BANK-RACE-" + UUID.randomUUID().toString().substring(0, 18));
            other.setRenter(original.getRenter()); other.setVehicle(original.getVehicle()); other.setPlan(original.getPlan());
            other.setPickupTime(original.getPickupTime().plusSeconds(86400)); other.setPickupAddress("Bank race fixture");
            other.setPassengerCount(1); other.setTotalAmount(original.getTotalAmount()); other.setStatus(BookingStatus.AWAITING_DEPOSIT);
            em.persist(other);
            return payments.createDepositPaymentRequest(other).getId();
        });
        String oneBody = reconciliationBody("40000", "CROSS-" + requestId, "CROSS-ONE-" + requestId);
        String twoBody = reconciliationBody("40000", "CROSS-" + requestId, "CROSS-TWO-" + requestId);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return reconcileHttpAt(requestId, providerId, oneBody).andReturn().getResponse().getStatus(); });
            var two = pool.submit(() -> { start.await(); return reconcileHttpAt(secondRequest, providerId, twoBody).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        long rows = tx.execute(s -> em.createQuery("select count(t) from PaymentTransaction t where t.gatewayRef = :r", Long.class)
                .setParameter("r", "CROSS-" + requestId).getSingleResult());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void bankExceptionDoesNotDiscardPendingGatewayAttempt() throws Exception {
        tx.executeWithoutResult(s -> {
            var pending = new PaymentTransaction(); pending.setBooking(em.find(Booking.class, bookingId));
            pending.setPaymentRequest(em.find(PaymentRequest.class, requestId)); pending.setType(PaymentType.DEPOSIT_PAY);
            pending.setDirection(PaymentDirection.IN); pending.setMethod(PaymentMethod.VNPAY); pending.setStatus(PaymentStatus.PENDING);
            pending.setAmount(new BigDecimal("30000")); pending.setVnpTxnRef("PENDING-" + requestId); em.persist(pending);
        });
        reconcileHttp(providerId, reconciliationBody("40000", "BANK-PENDING-" + requestId, "BANK-PENDING-" + requestId))
                .andExpect(status().isOk());
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(2).anySatisfy(t -> {
            assertThat(t.getMethod()).isEqualTo(PaymentMethod.VNPAY); assertThat(t.getStatus()).isEqualTo(PaymentStatus.PENDING);
        });
        assertThat(closure.reconcile(bookingId)).isFalse();
        assertThat(refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId)).isEmpty();
    }

    @Test
    void paidRequestCannotBeConfirmedAgain() {
        confirm("30000", "PAID-" + requestId);
        assertThatThrownBy(() -> confirm("30000", "PAID-AGAIN-" + requestId)).isInstanceOf(ConflictException.class);
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
    }

    @Test
    void repeatedPartialConfirmationWithoutBankReferenceCannotDoubleCount() {
        var request = new ConfirmPaymentRequest(new BigDecimal("10000"), null, "Manual statement receipt");
        payments.confirmPayment(requestId, providerId, request);
        assertThatThrownBy(() -> payments.confirmPayment(requestId, providerId, request))
                .isInstanceOf(InvalidPaymentOperationException.class);
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
        assertThat(requests.findById(requestId).orElseThrow().getPaidAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void finalPaymentNeedsBothEndConfirmationsAndCompletesWhenEligible() {
        tx.executeWithoutResult(s -> {
            Booking booking = em.find(Booking.class, bookingId);
            booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT); booking.setActualEndAt(Instant.now());
            booking.setProviderConfirmedAt(Instant.now()); booking.setRenterConfirmedAt(Instant.now());
            em.find(PaymentRequest.class, requestId).setPaymentType(PaymentRequestType.FINAL_BALANCE);
            em.find(PaymentRequest.class, requestId).setAmount(new BigDecimal("100000"));
        });
        confirm("100000", "FINAL-" + requestId);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(receipts.getReceipt(renterId, requestCode).status()).isEqualTo(PaymentReceiptStatus.SUCCESS);
    }

    @Test
    void staleFinalAmountRecordsMoneyForReconciliationInsteadOfCompleting() {
        tx.executeWithoutResult(s -> {
            Booking booking = em.find(Booking.class, bookingId);
            booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT); booking.setActualEndAt(Instant.now());
            booking.setProviderConfirmedAt(Instant.now()); booking.setRenterConfirmedAt(Instant.now());
            em.find(PaymentRequest.class, requestId).setPaymentType(PaymentRequestType.FINAL_BALANCE);
        });
        confirm("30000", "STALE-FINAL-" + requestId);
        assertReconciliation(BookingStatus.AWAITING_FINAL_PAYMENT);
    }

    @Test
    void duplicateConfirmationAndReconciliationReplayNeverRecordTwice() {
        confirm("30000", "FIRST-" + requestId);
        assertThatThrownBy(() -> confirm("30000", "ANOTHER-" + requestId)).isInstanceOf(ConflictException.class);
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
    }

    @Test
    void concurrentConfirmationsHaveOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of("ONE-", "TWO-").stream().map(prefix -> pool.submit(() -> {
                start.await();
                try { confirm("30000", prefix + requestId); return true; }
                catch (InvalidPaymentOperationException | ConflictException exception) { return false; }
            })).toList();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void receiptHttpReadsDatabaseAndRejectsOtherUsers(PaymentStatus paymentStatus) throws Exception {
        String reference = "VP" + UUID.randomUUID().toString().replace("-", "").substring(0, 25);
        tx.executeWithoutResult(s -> {
            PaymentTransaction payment = new PaymentTransaction(); payment.setBooking(em.find(Booking.class, bookingId));
            payment.setType(PaymentType.DEPOSIT_PAY); payment.setDirection(PaymentDirection.IN); payment.setMethod(PaymentMethod.VNPAY);
            payment.setStatus(paymentStatus); payment.setAmount(new BigDecimal("30000")); payment.setAppliedAmount(new BigDecimal("30000"));
            payment.setTransactionCode(reference); payment.setVnpTxnRef(reference); payment.setPaidAt(Instant.now());
            payment.setVerificationMethod(PaymentVerificationMethod.GATEWAY_VERIFIED); em.persist(payment);
        });
        String result = switch (paymentStatus) {
            case PAID -> "SUCCESS";
            case PENDING -> "PENDING_VERIFICATION";
            case FAILED -> "FAILED";
        };
        String token = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, renterId)));
        mvc.perform(get("/api/v1/payments/receipt").param("reference", reference).param("amount", "999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(result))
                .andExpect(jsonPath("$.data.amount").value(30000)).andExpect(jsonPath("$.data.transactionCode").value(reference));
        String outsiderToken = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, outsiderId)));
        mvc.perform(get("/api/v1/payments/receipt").param("reference", reference)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments/receipt").param("reference", reference)).andExpect(status().isUnauthorized());
    }

    @Test
    void mockedPaymentDoesNotBecomeVerifiedReceipt() {
        tx.executeWithoutResult(s -> {
            PaymentTransaction payment = new PaymentTransaction(); payment.setBooking(em.find(Booking.class, bookingId));
            payment.setType(PaymentType.DEPOSIT_PAY); payment.setDirection(PaymentDirection.IN); payment.setMethod(PaymentMethod.BANK_TRANSFER);
            payment.setStatus(PaymentStatus.PAID); payment.setAmount(new BigDecimal("30000")); payment.setAppliedAmount(new BigDecimal("30000"));
            payment.setPaidAt(Instant.now()); payment.setTransactionCode("MOCK-" + requestId);
            payment.setVerificationMethod(PaymentVerificationMethod.MOCK_SIMULATED); em.persist(payment);
        });
        assertThat(receipts.getReceipt(renterId, "MOCK-" + requestId).status()).isEqualTo(PaymentReceiptStatus.RECONCILIATION_REQUIRED);
    }
}
