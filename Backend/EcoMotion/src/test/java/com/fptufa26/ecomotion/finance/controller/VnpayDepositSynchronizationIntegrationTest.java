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
class VnpayDepositSynchronizationIntegrationTest {
    @Autowired com.fptufa26.ecomotion.finance.service.VnpayService vnpay;
    @Autowired com.fptufa26.ecomotion.finance.config.VnpayConfig config;
    @Autowired com.fptufa26.ecomotion.finance.service.PaymentReconciliationService reconciliation;
    @Autowired com.fptufa26.ecomotion.booking.service.BookingWorkflowService workflow;
    @Autowired com.fptufa26.ecomotion.booking.service.ProviderBookingService providerBookings;
    @Autowired com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService notifications;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
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
            plan.setSlotLengthMinutes(60); plan.setSlotPrice(new BigDecimal("3200000")); em.persist(plan);
            Booking booking = new Booking(); booking.setBookingCode(UUID.randomUUID().toString().substring(0, 30).toUpperCase(java.util.Locale.ROOT));
            booking.setRenter(renter); booking.setVehicle(vehicle); booking.setPlan(plan); booking.setPickupTime(Instant.now().plusSeconds(86400));
            booking.setPickupAddress("Test pickup"); booking.setPassengerCount(1); booking.setTotalAmount(new BigDecimal("3200000"));
            booking.setStatus(BookingStatus.AWAITING_DEPOSIT); em.persist(booking); bookingId = booking.getId();
            BookingPolicySnapshot snapshot = new BookingPolicySnapshot(); snapshot.setBooking(booking); snapshot.setPlanType(PlanType.HOURLY);
            snapshot.setSlotLengthMinutes(60); snapshot.setSlotPrice(plan.getSlotPrice()); snapshot.setDepositRatio(new BigDecimal("0.3"));
            snapshot.setDepositAmount(new BigDecimal("960000")); em.persist(snapshot);
            BookingCharge charge = new BookingCharge(); charge.setBooking(booking); charge.setChargeType(ChargeType.BASE_RENTAL);
            charge.setQuantity(1); charge.setUnitPrice(plan.getSlotPrice()); charge.setAmount(plan.getSlotPrice());
            charge.setDescription("Base rental fixture"); charge.setCreatedBy(provider.getUser()); em.persist(charge);
            schedule.hold(vehicle, booking, booking.getPickupTime(), booking.getPickupTime().plusSeconds(3600), Instant.now().plusSeconds(900));
            PaymentRequest request = payments.createDepositPaymentRequest(booking);
            requestId = request.getId(); requestCode = request.getPaymentCode();
        });
    }

    private java.util.Map<String, String> attempt() {
        String code = bookings.findById(bookingId).orElseThrow().getBookingCode();
        var result = vnpay.createPaymentUrl(renterId,
                new com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest(code, "DEPOSIT", null, "vn"), "127.0.0.1");
        var params = new java.util.HashMap<String, String>();
        params.put("vnp_TmnCode", config.getTmnCode()); params.put("vnp_TxnRef", result.vnpTxnRef());
        params.put("vnp_Amount", result.amount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_TransactionNo", "123456789"); params.put("vnp_ResponseCode", "00"); params.put("vnp_TransactionStatus", "00");
        params.put("vnp_PayDate", java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.now()));
        sign(params); return params;
    }

    private void sign(java.util.Map<String, String> params) {
        params.remove("vnp_SecureHash"); params.put("vnp_SecureHash", com.fptufa26.ecomotion.finance.config.VnpayUtils.hmacSHA512(
                config.getHashSecret(), com.fptufa26.ecomotion.finance.config.VnpayUtils.buildHashData(params)));
    }

    private String code() { return bookings.findById(bookingId).orElseThrow().getBookingCode(); }

    private void assertDepositApplied() {
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        var request = requests.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(request.getPaidAmount()).isEqualByComparingTo("960000"); assertThat(request.getConfirmedAt()).isNotNull();
        var payment = ledger.findByBooking_Id(bookingId).stream().filter(t -> t.getStatus() == PaymentStatus.PAID).findFirst().orElseThrow();
        assertThat(payment.getAppliedAmount()).isEqualByComparingTo("960000"); assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getVerificationMethod()).isEqualTo(PaymentVerificationMethod.GATEWAY_VERIFIED);
        assertThat(schedules.findByBooking_Id(bookingId)).allMatch(s -> s.getStatus() == ScheduleStatus.ACTIVE);
        var renter = workflow.getWorkflow(renterId, code()); var provider = workflow.getWorkflow(providerId, code());
        assertThat(renter.paymentStatus()).isEqualTo("DEPOSIT_PAID"); assertThat(provider.paymentStatus()).isEqualTo("DEPOSIT_PAID");
        assertThat(renter.confirmedPaid()).isEqualByComparingTo("960000");
        assertThat(renter.remainingAmount()).isEqualByComparingTo("2240000"); assertThat(provider.needsDriver()).isTrue();
        assertThat(providerBookings.getBookingDetail(providerId, code()).paymentStatus()).isEqualTo("DEPOSIT_PAID");
    }

    private String bearerFor(long id) {
        return "Bearer " + tx.execute(s -> jwt.generateAccessToken(em.find(User.class, id)));
    }

    @Test
    void queryRequiresBookingOwnershipAndIpnRemainsPublic() throws Exception {
        var params = attempt();
        String ref = params.get("vnp_TxnRef");
        long otherRenter = tx.execute(s -> user(Role.RENTER).getId());
        long otherDriver = tx.execute(s -> user(Role.DRIVER).getId());
        for (String base : List.of("/api/v1/payment/vnpay", "/api/payment/vnpay", "/v1/payment/vnpay", "/payment/vnpay")) {
            var query = get(base + "/query").param("bookingCode", code()).param("vnpTxnRef", ref);
            mvc.perform(query).andExpect(status().isUnauthorized());
            for (long owner : List.of(renterId, providerId)) {
                mvc.perform(get(base + "/query").param("bookingCode", code()).param("vnpTxnRef", ref)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(owner)))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.bankCode").doesNotExist())
                        .andExpect(jsonPath("$.data.cardType").doesNotExist());
            }
            for (long outsider : List.of(otherRenter, outsiderId, otherDriver)) {
                mvc.perform(get(base + "/query").param("bookingCode", code()).param("vnpTxnRef", ref)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(outsider))).andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/api/v1/payment/vnpay/query").param("bookingCode", code()).param("vnpTxnRef", "unknown")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(renterId))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payment/vnpay/query").param("bookingCode", "missing").param("vnpTxnRef", ref)
                .header(HttpHeaders.AUTHORIZATION, bearerFor(renterId))).andExpect(status().isNotFound());
        var ipn = get("/api/v1/payment/vnpay/ipn");
        params.forEach(ipn::param);
        mvc.perform(ipn).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
        assertDepositApplied();
    }

    @Test
    void candidateHttpResponseContainsNoPrivateDetails() throws Exception {
        tx.executeWithoutResult(s -> {
            var driver = new com.fptufa26.ecomotion.user.entity.Driver();
            driver.setUser(user(Role.DRIVER));
            driver.setProvider(em.find(Booking.class, bookingId).getVehicle().getProvider());
            driver.setLicenseNo("PRIVATE-TEST-LICENSE"); driver.setLicenseClass("B2");
            driver.setLicenseExpiry(java.time.LocalDate.now().plusYears(5));
            driver.setPortraitUrl("test"); driver.setLicenseImageUrl("test");
            driver.setStatus(com.fptufa26.ecomotion.user.enums.DriverStatus.APPROVED);
            driver.setActivityStatus(com.fptufa26.ecomotion.user.enums.DriverActivityStatus.AVAILABLE);
            em.persist(driver);
        });
        for (String base : List.of("/api/provider/bookings/", "/api/v1/provider/bookings/", "/api/v1/provider/trips/")) {
            String body = mvc.perform(get(base + code() + "/assign-candidates").header(HttpHeaders.AUTHORIZATION, bearerFor(providerId)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).contains("driverId", "eligible", "availabilityStatus")
                    .doesNotContain("PRIVATE-TEST-LICENSE", "licenseNo", "licenseExpiry", "phone", "currentBookingCode", "conflictingBookingCode", "nextBookingCode", "conflictStart", "conflictEnd");
            mvc.perform(get(base + code() + "/assign-candidates").header(HttpHeaders.AUTHORIZATION, bearerFor(outsiderId)))
                    .andExpect(status().isNotFound());
            mvc.perform(get(base + code() + "/assign-candidates").header(HttpHeaders.AUTHORIZATION, bearerFor(renterId)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void providerRequestShowsGatewayVerificationUntilIpnIsApplied() {
        var params = attempt();
        assertThat(payments.getPaymentRequestDetail(requestId, providerId).vnpayVerifying()).isTrue();
        assertThat(vnpay.processIpn(params).rspCode()).isEqualTo("00");
        var request = payments.getPaymentRequestDetail(requestId, providerId);
        assertThat(request.vnpayVerifying()).isFalse();
        assertThat(request.status()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertDepositApplied();
    }

    @Test
    void validIpnAppliesDepositAndDuplicateCannotDoubleCount() {
        var params = attempt(); assertThat(vnpay.processIpn(params).rspCode()).isEqualTo("00"); assertDepositApplied();
        assertThat(vnpay.processIpn(params).rspCode()).isEqualTo("02");
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1); assertDepositApplied();
    }

    @Test
    void concurrentIpnHasOneApplicationAndOneDuplicate() throws Exception {
        var params = attempt(); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { start.await(); return vnpay.processIpn(params).rspCode(); });
            var two = pool.submit(() -> { start.await(); return vnpay.processIpn(params).rspCode(); });
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder("00", "02");
        }
        assertThat(ledger.findByBooking_Id(bookingId)).hasSize(1); assertDepositApplied();
    }

    @Test
    void browserReturnBeforeIpnAndRefreshingAreReadOnly() {
        var params = attempt();
        assertThat(vnpay.processCallback(params).status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(vnpay.processCallback(params).status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        assertThat(ledger.findByBooking_Id(bookingId).getFirst().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(vnpay.processIpn(params).rspCode()).isEqualTo("00");
        assertThat(vnpay.processCallback(params).status()).isEqualTo("SUCCESS"); assertDepositApplied();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"expired", "dispute", "cancelled", "amount", "checksum"})
    void invalidConditionsNeverConfirmBooking(String condition) {
        var params = attempt();
        tx.executeWithoutResult(s -> {
            if (condition.equals("expired")) schedules.findByBooking_Id(bookingId).forEach(h -> h.setExpiresAt(Instant.now().minusSeconds(1)));
            if (condition.equals("cancelled")) { var b = em.find(Booking.class, bookingId); b.setStatus(BookingStatus.CANCELLED); b.setCancelledAt(Instant.now()); }
            if (condition.equals("dispute")) {
                var d = new Dispute(); d.setBooking(em.find(Booking.class, bookingId)); d.setRaisedBy(em.find(User.class, renterId));
                d.setCategory(DisputeCategory.CHARGE); d.setStatus(DisputeStatus.OPEN); d.setDescription("P0 fixture dispute"); em.persist(d);
            }
        });
        if (condition.equals("amount")) { params.put("vnp_Amount", "1"); sign(params); }
        if (condition.equals("checksum")) params.put("vnp_SecureHash", "bad-signature");
        vnpay.processIpn(params);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isNotEqualTo(BookingStatus.CONFIRMED);
        var payment = ledger.findByBooking_Id(bookingId).getFirst(); assertThat(payment.getAppliedAmount()).isZero();
        if (!condition.equals("amount") && !condition.equals("checksum")) {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.getProcessingNote()).startsWith("RECONCILIATION_REQUIRED");
        } else assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void stalePendingAttemptCannotMaskVerifiedDepositEvenWithDifferentAmount() {
        var params = attempt(); vnpay.processIpn(params);
        tx.executeWithoutResult(s -> {
            var old = new PaymentTransaction(); old.setBooking(em.find(Booking.class, bookingId)); old.setType(PaymentType.DEPOSIT_PAY);
            old.setMethod(PaymentMethod.VNPAY); old.setDirection(PaymentDirection.IN); old.setAmount(new BigDecimal("3200000"));
            old.setStatus(PaymentStatus.PENDING); old.setVnpTxnRef("stale-" + UUID.randomUUID());
            old.setCreatedAt(Instant.now().minusSeconds(3600)); em.persist(old);
        });
        assertDepositApplied();
    }

    @Test
    void queryDrAndScheduledReconciliationApplyOnlySignedGatewayResponse() throws Exception {
        var params = attempt(); String reference = params.get("vnp_TxnRef");
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var response = new java.util.HashMap<>(params);
        response.put("vnp_ResponseId", "p0-query-response"); response.put("vnp_Command", "querydr");
        response.put("vnp_Message", "Success"); response.put("vnp_TransactionType", "01"); response.put("vnp_OrderInfo", "Query payment " + reference);
        String data = java.util.stream.Stream.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef",
                "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
                "vnp_PromotionCode", "vnp_PromotionAmount").map(key -> response.getOrDefault(key, "")).collect(java.util.stream.Collectors.joining("|"));
        response.put("vnp_SecureHash", com.fptufa26.ecomotion.finance.config.VnpayUtils.hmacSHA512(config.getHashSecret(), data));
        server.createContext("/query", exchange -> {
            exchange.getRequestBody().readAllBytes(); byte[] bytes = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start(); String previousUrl = config.getApiUrl(); var previousInterval = config.getQueryInterval();
        try {
            config.setReconciliationEnabled(true); config.setQueryInterval(java.time.Duration.ZERO);
            config.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/query");
            assertThat(vnpay.processCallback(params).status()).isEqualTo("PENDING_VERIFICATION");
            reconciliation.reconcilePending(); assertDepositApplied();
        } finally { config.setReconciliationEnabled(false); config.setApiUrl(previousUrl); config.setQueryInterval(previousInterval); server.stop(0); }
    }

    @Test
    void receivedButUnappliedFundsAreNotSubtractedFromContractBalance() {
        var params = attempt(); tx.executeWithoutResult(s -> schedule.release(bookingId)); vnpay.processIpn(params);
        var state = workflow.getWorkflow(renterId, code());
        assertThat(state.paymentStatus()).isEqualTo("RECONCILIATION_REQUIRED"); assertThat(state.confirmedPaid()).isZero();
    }

    @Test
    void legacyPaidRowWithoutAllocationRequiresReviewInsteadOfInventingAppliedMoney() {
        tx.executeWithoutResult(s -> {
            var payment = new PaymentTransaction(); payment.setBooking(em.find(Booking.class, bookingId));
            payment.setDirection(PaymentDirection.IN); payment.setType(PaymentType.DEPOSIT_PAY); payment.setMethod(PaymentMethod.VNPAY);
            payment.setStatus(PaymentStatus.PAID); payment.setVerificationMethod(PaymentVerificationMethod.GATEWAY_VERIFIED);
            payment.setAmount(new BigDecimal("960000")); payment.setAppliedAmount(null); payment.setPaidAt(Instant.now()); em.persist(payment);
        });
        var state = workflow.getWorkflow(renterId, code());
        assertThat(state.paymentStatus()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(state.confirmedPaid()).isZero(); assertThat(state.remainingAmount()).isEqualByComparingTo("3200000");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
    }

    @Test
    void feeDifferenceComesFromUnwaivedChargesAndDepositReducesTheBalance() {
        tx.executeWithoutResult(s -> {
            var charge = new BookingCharge(); charge.setBooking(em.find(Booking.class, bookingId)); charge.setChargeType(ChargeType.WAITING);
            charge.setQuantity(1); charge.setUnitPrice(new BigDecimal("40000")); charge.setAmount(new BigDecimal("40000"));
            charge.setDescription("Test fixture fee, not evidence of the missing live booking");
            charge.setCreatedBy(em.find(User.class, providerId)); em.persist(charge);
        });
        assertThat(workflow.getWorkflow(providerId, code()).remainingAmount()).isEqualByComparingTo("3240000");
        vnpay.processIpn(attempt()); assertThat(workflow.getWorkflow(providerId, code()).remainingAmount()).isEqualByComparingTo("2280000");
    }

    @Test
    void rollbackRestoresPendingStateAndBothParticipantsAreNotifiedOnlyAfterCommit() throws Exception {
        var params = attempt();
        var provider = org.mockito.Mockito.mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        var renter = org.mockito.Mockito.mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        notifications.registerEmitter(providerId, provider); notifications.registerEmitter(renterId, renter);
        org.mockito.Mockito.clearInvocations(provider, renter);
        try {
            tx.executeWithoutResult(s -> {
                vnpay.processIpn(params); s.setRollbackOnly();
                try { org.mockito.Mockito.verify(provider, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class)); }
                catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            });
            assertThat(ledger.findByBooking_Id(bookingId).getFirst().getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(requests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PaymentRequestStatus.PENDING);
            org.mockito.Mockito.verify(renter, org.mockito.Mockito.never()).send(org.mockito.ArgumentMatchers.any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
            vnpay.processIpn(params); assertDepositApplied();
            org.mockito.Mockito.verify(provider, org.mockito.Mockito.atLeastOnce()).send(org.mockito.ArgumentMatchers.any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
            org.mockito.Mockito.verify(renter, org.mockito.Mockito.atLeastOnce()).send(org.mockito.ArgumentMatchers.any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
        } finally { notifications.removeEmitter(providerId, provider); notifications.removeEmitter(renterId, renter); }
    }
}
