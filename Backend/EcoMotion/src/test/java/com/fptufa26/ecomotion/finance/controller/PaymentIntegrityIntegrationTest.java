package com.fptufa26.ecomotion.finance.controller;

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
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
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
    @Autowired VehicleScheduleRepository schedules;
    @Autowired ScheduleService schedule;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
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
        return user;
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
    void expiredHoldRecordsMoneyWithoutActivatingSchedule() {
        tx.executeWithoutResult(s -> schedules.findByBooking_Id(bookingId).forEach(h -> h.setExpiresAt(Instant.now().minusSeconds(1))));
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
    void terminalBookingKeepsReceivedMoneyWithoutResurrection(BookingStatus status) {
        tx.executeWithoutResult(s -> {
            em.find(Booking.class, bookingId).setStatus(status);
            em.find(PaymentRequest.class, requestId).setStatus(PaymentRequestStatus.VOIDED);
            schedule.release(bookingId);
        });
        confirm("30000", "TERMINAL-" + requestId);
        assertReconciliation(status);
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
    void invalidAmountAndWrongProviderDoNotRecordMoney() {
        assertThatThrownBy(() -> confirm("0", "INVALID")).isInstanceOf(InvalidPaymentOperationException.class);
        assertThatThrownBy(() -> payments.confirmPayment(requestId, outsiderId,
                new ConfirmPaymentRequest(new BigDecimal("30000"), "OTHER", null))).isInstanceOf(AccessDeniedException.class);
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @Test
    void partialPaymentsGetDistinctCodesAndOnlyCompleteAfterFullAmount() {
        confirm("10000", "PARTIAL-1-" + requestId);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        confirm("20000", "PARTIAL-2-" + requestId);
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(2).extracting(PaymentTransaction::getTransactionCode).doesNotHaveDuplicates();
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void paidRequestCannotBeConfirmedAgain() {
        confirm("30000", "PAID-" + requestId);
        assertThatThrownBy(() -> confirm("30000", "PAID-AGAIN-" + requestId)).isInstanceOf(InvalidPaymentOperationException.class);
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
        tx.executeWithoutResult(s -> em.find(PaymentRequest.class, requestId).setStatus(PaymentRequestStatus.EXPIRED));
        confirm("30000", "LATE-" + requestId);
        assertThatThrownBy(() -> confirm("30000", "ANOTHER-" + requestId)).isInstanceOf(InvalidPaymentOperationException.class);
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
    }

    @Test
    void concurrentConfirmationsHaveOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of("ONE-", "TWO-").stream().map(prefix -> pool.submit(() -> {
                start.await();
                try { confirm("30000", prefix + requestId); return true; }
                catch (InvalidPaymentOperationException exception) { return false; }
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
