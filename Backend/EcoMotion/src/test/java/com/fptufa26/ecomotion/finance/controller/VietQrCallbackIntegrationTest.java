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

@SpringBootTest(properties = {"logging.level.org.hibernate.SQL=WARN", "app.vietqr.enabled=true",
        "app.vietqr.callback-username=callback-test", "app.vietqr.callback-password=callback-test-password",
        "app.vietqr.callback-secret=callback-only-test-secret-with-32-characters"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VietQrCallbackIntegrationTest {
    @Autowired com.fptufa26.ecomotion.finance.service.VietQrPaymentService qrPayments;
    @Autowired com.fptufa26.ecomotion.finance.service.VietQrTransactionSyncService sync;
    @Autowired com.fptufa26.ecomotion.finance.service.VietQrCallbackAuthService callbackAuth;
    @Autowired com.fptufa26.ecomotion.finance.repository.VietQrCallbackRepository callbacks;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @Autowired com.fptufa26.ecomotion.finance.service.ProviderFinanceService providerFinance;
    @Autowired com.fptufa26.ecomotion.booking.service.RenterPaymentService renterPayments;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.fptufa26.ecomotion.finance.service.gateway.VietQrPaymentGatewayStrategy gateway;
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
    @Autowired com.fptufa26.ecomotion.finance.service.VnpayService vnpay;
    @Autowired com.fptufa26.ecomotion.finance.config.VnpayConfig vnpayConfig;
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

    private void issue() {
        tx.executeWithoutResult(s -> {
            var bank = em.find(PaymentRequest.class, requestId).getReceivingBankAccount();
            bank.setVietQrLinkedAt(Instant.now());
        });
        org.mockito.Mockito.when(gateway.generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> new com.fptufa26.ecomotion.finance.service.gateway.VietQrPaymentGatewayStrategy.DynamicQr(
                        call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(4),
                        call.getArgument(0), "000201010212fixture", "https://pro.vietqr.vn/fixture"));
        qrPayments.create(requestId, renterId);
    }

    private com.fptufa26.ecomotion.finance.dto.request.VietQrTransactionSyncRequest callback(String id, String amount,
            String content, String account, String order, String direction) {
        return new com.fptufa26.ecomotion.finance.dto.request.VietQrTransactionSyncRequest(id, Instant.now().toEpochMilli(),
                "REF-" + id, new BigDecimal(amount), content, account, order, direction);
    }

    private com.fptufa26.ecomotion.finance.dto.request.VietQrTransactionSyncRequest valid(String id) {
        return callback(id, "30000", requestCode, "0123456789", requestCode, "C");
    }

    @Test
    void callbackConfirmsDepositAndDuplicateHasNoRevenueEffect() {
        issue();
        var input = valid("happy-" + requestId);
        assertThat(sync.synchronize(input)).isEqualTo("CONFIRMED");
        assertThat(sync.synchronize(input)).isEqualTo("CONFIRMED");
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(schedules.findByBooking_Id(bookingId)).allMatch(s -> s.getStatus() == ScheduleStatus.ACTIVE && s.getExpiresAt() == null);
        var payment = ledger.findByBooking_Id(bookingId).getFirst();
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(payment.getVerificationMethod()).isEqualTo(PaymentVerificationMethod.VIETQR_CALLBACK);
        assertThat(payment.getAppliedAmount()).isEqualByComparingTo("30000");
        assertThat(receipts.getReceipt(renterId, requestCode).status()).isEqualTo(PaymentReceiptStatus.SUCCESS);
        assertThat(providerFinance.getFinanceSummary(providerId).directReceived()).isEqualByComparingTo("30000");
        assertThat(providerFinance.getFinanceSummary(providerId).platformReceived()).isZero();
        assertThat(providerFinance.getFinanceSummary(providerId).totalConfirmedRevenue()).isEqualByComparingTo("30000");
    }

    @Test
    void repeatedCreateReturnsSameIssuedQrAndManualWritersAreBlocked() throws Exception {
        issue();
        assertThat(qrPayments.create(requestId, renterId).qrCodePayload()).isEqualTo("000201010212fixture");
        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1)).generate(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> payments.confirmPayment(requestId, providerId, new ConfirmPaymentRequest(null, "FORGED", null)))
                .isInstanceOf(InvalidPaymentOperationException.class);
        assertThatThrownBy(() -> payments.reissuePaymentRequest(requestId, renterId)).isInstanceOf(InvalidPaymentOperationException.class);
        String code = tx.execute(s -> em.find(Booking.class, bookingId).getBookingCode());
        assertThatThrownBy(() -> vnpay.createPaymentUrl(renterId,
                new com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest(code, "DEPOSIT", null, "vn"), "127.0.0.1"))
                .isInstanceOf(InvalidPaymentOperationException.class);
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @Test
    void unlinkedAccountAndOtherUsersCannotCreateQr() {
        assertThatThrownBy(() -> qrPayments.create(requestId, renterId)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> qrPayments.create(requestId, outsiderId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> qrPayments.status(requestCode, outsiderId)).isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"20000", "40000"})
    void wrongAmountsAreReceivedButNeverApplied(String amount) {
        issue();
        assertThat(sync.synchronize(callback("amount-" + requestId, amount, requestCode, "0123456789", requestCode, "C")))
                .startsWith("RECONCILIATION_REQUIRED");
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.REVIEW_REQUIRED);
        assertThat(ledger.findByBooking_Id(bookingId).getFirst().getAppliedAmount()).isZero();
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        assertThat(payments.calculateRemainingAmount(bookingId)).isEqualByComparingTo("100000");
        String code = bookings.findById(bookingId).orElseThrow().getBookingCode();
        assertThat(providerFinance.getBookingBreakdown(providerId, code).remainingAmount()).isEqualByComparingTo("100000");
        assertThat(renterPayments.getBookingCharges(renterId, code).summary().remainingAmount()).isEqualByComparingTo("100000");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"content", "account", "debit", "order"})
    void unmatchedTransactionsAreAuditedWithoutApplyingMoney(String mismatch) {
        issue();
        var input = callback("mismatch-" + requestId, "30000", mismatch.equals("content") ? "BAD" : requestCode,
                mismatch.equals("account") ? "9999" : "0123456789", mismatch.equals("order") ? "UNKNOWN" : requestCode,
                mismatch.equals("debit") ? "D" : "C");
        assertThat(sync.synchronize(input)).startsWith("REVIEW_REQUIRED");
        assertThat(callbacks.findByTransactionId(input.transactionid())).isPresent();
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"expired", "cancelled", "hold", "dispute", "overlap"})
    void invalidBookingKeepsReceivedFundsForReconciliation(String state) {
        issue();
        tx.executeWithoutResult(s -> {
            Booking booking = em.find(Booking.class, bookingId);
            if (state.equals("expired")) em.find(PaymentRequest.class, requestId).setExpiresAt(Instant.now().minusSeconds(1));
            if (state.equals("cancelled")) { booking.setStatus(BookingStatus.CANCELLED); booking.setCancelledAt(Instant.now()); }
            if (state.equals("hold")) schedules.findByBooking_Id(bookingId).forEach(h -> h.setExpiresAt(Instant.now().minusSeconds(1)));
            if (state.equals("dispute")) {
                Dispute dispute = new Dispute(); dispute.setBooking(booking); dispute.setRaisedBy(booking.getRenter().getUser());
                dispute.setCategory(DisputeCategory.CHARGE); dispute.setDescription("Callback fixture dispute");
                dispute.setStatus(DisputeStatus.OPEN); em.persist(dispute);
            }
            if (state.equals("overlap")) {
                VehicleSchedule overlap = new VehicleSchedule(); overlap.setVehicle(booking.getVehicle());
                overlap.setType(ScheduleType.MAINTENANCE); overlap.setStatus(ScheduleStatus.ACTIVE);
                overlap.setStartAt(booking.getPickupTime()); overlap.setEndAt(booking.getPickupTime().plusSeconds(3600)); em.persist(overlap);
            }
        });
        assertThat(sync.synchronize(valid("invalid-" + requestId))).startsWith("RECONCILIATION_REQUIRED");
        assertThat(ledger.findByBooking_Id(bookingId).getFirst().getAppliedAmount()).isZero();
        assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.REVIEW_REQUIRED);
        assertThat(receipts.getReceipt(renterId, requestCode).status()).isEqualTo(PaymentReceiptStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    void concurrentCallbacksAndChangedReplayCannotDoubleApply() throws Exception {
        issue();
        var input = valid("parallel-" + requestId);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return sync.synchronize(input); });
            var second = pool.submit(() -> { start.await(); return sync.synchronize(input); });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsOnly("CONFIRMED");
        }
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1);
        assertThatThrownBy(() -> sync.synchronize(callback(input.transactionid(), "40000", requestCode, "0123456789", requestCode, "C")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(sync.synchronize(valid("second-transfer-" + requestId))).startsWith("RECONCILIATION_REQUIRED");
        assertThat(ledger.findByBooking_Id(bookingId).stream().map(PaymentTransaction::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("30000");
    }

    @Test
    void concurrentSeparateTransfersApplyOneObligationAndKeepTheOtherForReview() throws Exception {
        issue();
        var one = valid("one-" + requestId); var two = valid("two-" + requestId);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return sync.synchronize(one); });
            var second = pool.submit(() -> { start.await(); return sync.synchronize(two); });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .anyMatch("CONFIRMED"::equals).anyMatch(value -> value.startsWith("RECONCILIATION_REQUIRED"));
        }
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(2);
        assertThat(ledger.findByBooking_Id(bookingId).stream().map(PaymentTransaction::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("30000");
        assertThat(providerFinance.getFinanceSummary(providerId).totalConfirmedRevenue()).isEqualByComparingTo("30000");
    }

    @Test
    void concurrentUnknownOrderDuplicatesHaveOneAuditRecord() throws Exception {
        var input = callback("unknown-" + requestId, "30000", "VQ999999", "0123456789", "VQ999999", "C");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return sync.synchronize(input); });
            var second = pool.submit(() -> { start.await(); return sync.synchronize(input); });
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsOnly("REVIEW_REQUIRED: UNKNOWN_ORDER");
        }
        assertThat(callbacks.findByTransactionId(input.transactionid())).isPresent();
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
    }

    @Test
    void rollbackRemovesAuditLedgerAndScheduleChangesThenRetrySucceeds() {
        issue(); var input = valid("rollback-" + requestId);
        tx.executeWithoutResult(s -> { sync.synchronize(input); s.setRollbackOnly(); });
        assertThat(callbacks.findByTransactionId(input.transactionid())).isEmpty();
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
        assertThat(schedules.findByBooking_Id(bookingId)).allMatch(h -> h.getStatus() == ScheduleStatus.HELD);
        assertThat(sync.synchronize(input)).isEqualTo("CONFIRMED");
    }

    @Test
    void finalPaymentCompletesOnlyAfterBothPartiesConfirmedEnd() {
        tx.executeWithoutResult(s -> {
            var booking = em.find(Booking.class, bookingId);
            booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT); booking.setActualEndAt(Instant.now());
            booking.setProviderConfirmedAt(Instant.now()); booking.setRenterConfirmedAt(Instant.now());
            var request = em.find(PaymentRequest.class, requestId);
            request.setPaymentType(PaymentRequestType.FINAL_BALANCE); request.setAmount(new BigDecimal("100000"));
        });
        issue();
        assertThat(sync.synchronize(callback("final-" + requestId, "100000", requestCode, "0123456789", requestCode, "C"))).isEqualTo("CONFIRMED");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(receipts.getReceipt(renterId, requestCode).status()).isEqualTo(PaymentReceiptStatus.SUCCESS);
    }

    @Test
    void anotherGatewayReceiptCannotBeAppliedTwiceAndPendingVnpayBlocksQr() {
        tx.executeWithoutResult(s -> {
            var payment = new PaymentTransaction(); payment.setBooking(em.find(Booking.class, bookingId));
            payment.setAmount(new BigDecimal("30000")); payment.setAppliedAmount(BigDecimal.ZERO);
            payment.setType(PaymentType.DEPOSIT_PAY); payment.setDirection(PaymentDirection.IN);
            payment.setStatus(PaymentStatus.PENDING); payment.setMethod(PaymentMethod.VNPAY); em.persist(payment);
        });
        assertThatThrownBy(this::issue).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        org.mockito.Mockito.verifyNoInteractions(gateway);
        tx.executeWithoutResult(s -> ledger.findByBooking_Id(bookingId).forEach(t -> t.setStatus(PaymentStatus.FAILED)));
        issue();
        tx.executeWithoutResult(s -> ledger.findByBooking_Id(bookingId).forEach(t -> {
            t.setStatus(PaymentStatus.PAID); t.setAppliedAmount(new BigDecimal("30000"));
        }));
        assertThat(sync.synchronize(valid("cross-gateway-" + requestId))).startsWith("RECONCILIATION_REQUIRED");
        assertThat(ledger.findByBooking_Id(bookingId).stream().map(PaymentTransaction::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("30000");
    }

    @Test
    void qrTimeoutRollsBackIssuanceAndAllowsRetry() {
        tx.executeWithoutResult(s -> em.find(PaymentRequest.class, requestId).getReceivingBankAccount().setVietQrLinkedAt(Instant.now()));
        org.mockito.Mockito.when(gateway.generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> qrPayments.create(requestId, renterId)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(requests.findById(requestId).orElseThrow().isVietQrManaged()).isFalse();
        assertThat(ledger.findByBooking_Id(bookingId)).isEmpty();
        org.mockito.Mockito.reset(gateway); issue();
        assertThat(qrPayments.status(requestCode, renterId).vietQrManaged()).isTrue();
    }

    @Test
    void bankChangesCannotRewriteIssuedSnapshotOrConfirmByHand() {
        issue();
        tx.executeWithoutResult(s -> {
            var request = em.find(PaymentRequest.class, requestId);
            request.getReceivingBankAccount().setAccountNumber("9999999999");
            request.getReceivingBankAccount().setVietQrLinkedAt(null);
            request.setAccountNumberSnapshot("9999999999");
        });
        assertThat(requests.findById(requestId).orElseThrow().getAccountNumberSnapshot()).isEqualTo("0123456789");
        assertThat(sync.synchronize(valid("snapshot-" + requestId))).isEqualTo("CONFIRMED");
        assertThat(qrPayments.callbacks(providerId, 0).getContent()).hasSize(1);
        assertThat(qrPayments.callbacks(outsiderId, 0).getContent()).isEmpty();
    }

    @Test
    void callbackHttpUsesDedicatedTokenAndNeverRenterJwt() throws Exception {
        issue(); var input = valid("http-" + requestId);
        String url = "/api/v1/integrations/vietqr/transaction-sync";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType("application/json").content(mapper.writeValueAsString(input))).andExpect(status().isUnauthorized());
        String renterJwt = tx.execute(s -> jwt.generateAccessToken(em.find(User.class, renterId)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + renterJwt)
                .contentType("application/json").content(mapper.writeValueAsString(input))).andExpect(status().isUnauthorized());
        String basic = "Basic " + java.util.Base64.getEncoder().encodeToString("callback-test:callback-test-password".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/integrations/vietqr/token_generate")
                .header(HttpHeaders.AUTHORIZATION, basic)).andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.error").value(false));
        mvc.perform(get("/api/v1/payments/" + requestCode + "/status").header(HttpHeaders.AUTHORIZATION, "Bearer " + renterJwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }
}
