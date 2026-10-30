package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.DriverAssignment;
import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.DriverAssignmentRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.*;
import com.fptufa26.ecomotion.notification.external.service.ExternalNotificationDispatcherService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "tv5.postgres", matches = "true")
class Tv5PostgresConcurrencyTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
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
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fptufa26.ecomotion.common.security.JwtService jwt;
    @Autowired PlatformTransactionManager manager;
    @Autowired DriverOptimizationService optimization;
    @Autowired DriverTripService trips;
    @Autowired BookingService renterBookings;
    @Autowired ProviderBookingService providerBookings;
    @Autowired com.fptufa26.ecomotion.finance.service.RefundService refunds;
    @Autowired com.fptufa26.ecomotion.finance.service.VnpayService vnpay;
    @MockitoBean com.fptufa26.ecomotion.finance.service.gateway.PaymentGatewayResolver gatewayResolver;
    @Autowired com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository ledger;
    @Autowired com.fptufa26.ecomotion.finance.repository.RefundRequestRepository refundRequests;
    @Autowired BookingRepository bookings;
    @Autowired DriverAssignmentRepository assignments;
    @Autowired javax.sql.DataSource dataSource;
    @MockitoBean MapGeocodingService geocoding;
    @MockitoBean ExternalNotificationDispatcherService external;
    TransactionTemplate tx;
    long first;
    long second;
    long driver;
    long provider;
    long driverUser;
    long renterUser;
    long providerUser;

    User user(Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@tv5.test");
        user.setPasswordHash("test-only");
        user.setFullName("TV5 test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        return user;
    }

    @BeforeEach
    void fixtures() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            Provider p = new Provider();
            p.setUser(user(Role.PROVIDER)); p.setProviderType(ProviderType.INDIVIDUAL);
            p.setDisplayName("TV5"); p.setHotline("0900000000"); p.setDepotAddress("Test depot");
            p.setCommissionRate(new BigDecimal("0.1")); p.setDepositRatio(new BigDecimal("0.3"));
            p.setStatus(ProviderStatus.APPROVED); em.persist(p); provider = p.getId(); providerUser = p.getUser().getId();
            Driver d = new Driver(); d.setUser(user(Role.DRIVER)); d.setProvider(p);
            d.setLicenseNo(UUID.randomUUID().toString().substring(0, 18)); d.setLicenseClass("B2");
            d.setLicenseExpiry(LocalDate.now().plusYears(5)); d.setPortraitUrl("test"); d.setLicenseImageUrl("test");
            d.setStatus(DriverStatus.APPROVED); d.setActivityStatus(DriverActivityStatus.AVAILABLE);
            em.persist(d); driver = d.getId(); driverUser = d.getUser().getId();
            Renter renter = new Renter(); renter.setUser(user(Role.RENTER)); em.persist(renter); renterUser = renter.getUser().getId();
            Vehicle v = new Vehicle(); v.setProvider(p); v.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            v.setChassisNo(UUID.randomUUID().toString()); v.setEngineNo(UUID.randomUUID().toString());
            v.setBrand("Test"); v.setModel("TV5"); v.setManufactureYear(2026); v.setSeatCount(4);
            v.setTransmission(Transmission.AUTOMATIC); v.setColor("White"); v.setFuelType(FuelType.EV);
            v.setMaintenanceBufferMinutes(0); v.setStatus(VehicleStatus.APPROVED); em.persist(v);
            RentalPlan plan = new RentalPlan(); plan.setVehicle(v); plan.setPlanType(PlanType.HOURLY);
            plan.setSlotLengthMinutes(60); plan.setSlotPrice(new BigDecimal("100000")); em.persist(plan);
            for (int i = 0; i < 2; i++) {
                Booking b = new Booking(); b.setBookingCode(UUID.randomUUID().toString().substring(0, 30).toUpperCase(java.util.Locale.ROOT));
                b.setRenter(renter); b.setVehicle(v); b.setPlan(plan); b.setStatus(BookingStatus.CONFIRMED);
                b.setPickupTime(Instant.now().plusSeconds(86400)); b.setPickupAddress("Test pickup");
                b.setPassengerCount(1); b.setTotalAmount(new BigDecimal("100000")); em.persist(b);
                var policy = new com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot();
                policy.setBooking(b); policy.setSlotLengthMinutes(60); policy.setSlotPrice(new BigDecimal("100000"));
                policy.setPlanType(PlanType.HOURLY); em.persist(policy);
                var base = new com.fptufa26.ecomotion.booking.entity.BookingCharge(); base.setBooking(b);
                base.setChargeType(com.fptufa26.ecomotion.booking.enums.ChargeType.BASE_RENTAL); base.setQuantity(1);
                base.setDescription("Test base rental"); base.setUnitPrice(new BigDecimal("100000"));
                base.setAmount(new BigDecimal("100000")); base.setCreatedBy(p.getUser()); em.persist(base);
                if (i == 0) first = b.getId(); else second = b.getId();
            }
        });
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migrations/20261029_tv5_refund_reference.sql"))
                .execute(dataSource);
    }

    List<Boolean> race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = List.of(a, b).stream().map(task -> pool.submit(() -> {
                start.await();
                try { task.run(); return true; }
                catch (com.fptufa26.ecomotion.common.exception.ConflictException
                       | com.fptufa26.ecomotion.booking.exception.TripConflictException
                       | com.fptufa26.ecomotion.booking.exception.InvalidTripTransitionException
                       | org.springframework.security.access.AccessDeniedException
                       | org.springframework.dao.DataIntegrityViolationException
                       | com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException exception) { return false; }
            })).toList();
            start.countDown();
            return List.of(tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void overlappingManualAssignmentsHaveOneWinner() throws Exception {
        assertThat(race(() -> optimization.manualAssign(first, driver, provider),
                () -> optimization.manualAssign(second, driver, provider))).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void manualVersusAutoCannotDoubleBook() throws Exception {
        race(() -> optimization.manualAssign(first, driver, provider), () -> optimization.autoAssign(second));
        assertThat(tx.<Long>execute(status -> bookings.findAll().stream()
                .filter(b -> b.getDriver() != null && b.getDriver().getId() == driver).count())).isEqualTo(1L);
    }

    @Test
    void repeatedConcurrentAutoCreatesOnePendingAssignment() throws Exception {
        assertThat(race(() -> optimization.autoAssign(first), () -> optimization.autoAssign(first))).containsOnly(true);
        assertThat(assignments.findByBooking_IdOrderByCreatedAtDesc(first)).hasSize(1);
    }

    @Test
    void staleTimeoutCannotReplaceCurrentAssignment() {
        optimization.manualAssign(first, driver, provider);
        tx.executeWithoutResult(status -> {
            Booking b = bookings.findByIdForUpdate(first).orElseThrow();
            DriverAssignment old = new DriverAssignment(); old.setBooking(b); old.setDriver(b.getDriver());
            old.setAssignedAt(Instant.now().minusSeconds(7200)); old.setExpiresAt(Instant.now().minusSeconds(3600));
            old.setStatus(AssignmentStatus.PENDING_RESPONSE); assignments.save(old);
        });
        optimization.processPendingAssignmentTimeouts();
        assertThat(tx.<Long>execute(status -> bookings.findById(first).orElseThrow().getDriver().getId())).isEqualTo(driver);
        assertThat(assignments.findByBooking_IdOrderByCreatedAtDesc(first))
                .filteredOn(a -> a.getStatus() == AssignmentStatus.PENDING_RESPONSE).hasSize(1);
    }

    @Test
    void rejectVersusConfirmAllowsOnlyOneTransition() throws Exception {
        optimization.manualAssign(first, driver, provider);
        var result = race(() -> trips.confirmTrip(driverUser, String.valueOf(first)),
                () -> trips.rejectTrip(driverUser, String.valueOf(first),
                        new com.fptufa26.ecomotion.booking.dto.request.DriverRejectRequest(
                                com.fptufa26.ecomotion.booking.enums.DriverRejectReason.OTHER, "Test")));
        assertThat(result).containsExactlyInAnyOrder(true, false);
        assertThat(assignments.findByBooking_IdOrderByCreatedAtDesc(first))
                .filteredOn(a -> a.getStatus() == AssignmentStatus.PENDING_RESPONSE).isEmpty();
    }
    @Test
    void startAndEndFromBothActorsAreSerialized() throws Exception {
        optimization.manualAssign(first, driver, provider);
        tx.executeWithoutResult(status -> {
            Booking b = bookings.findByIdForUpdate(first).orElseThrow();
            b.setStatus(BookingStatus.DRIVER_ARRIVED); b.setDriverConfirmedAt(Instant.now()); b.setActualArrivedAt(Instant.now());
        });
        String code = tx.execute(status -> bookings.findById(first).orElseThrow().getBookingCode());
        assertThat(race(() -> trips.startTrip(driverUser, code), () -> renterBookings.confirmStart(renterUser, code))).containsOnly(true);
        assertThat(bookings.findById(first).orElseThrow().getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        assertThat(race(() -> trips.finishTrip(driverUser, code), () -> renterBookings.confirmEnd(renterUser, code))).containsOnly(true);
        Booking ended = bookings.findById(first).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        assertThat(ended.getActualStartAt()).isNotNull(); assertThat(ended.getActualEndAt()).isNotNull();
    }

    @Test
    void expiredConfirmCannotBeatTimeout() throws Exception {
        optimization.manualAssign(first, driver, provider);
        tx.executeWithoutResult(status -> {
            Booking b = bookings.findByIdForUpdate(first).orElseThrow();
            Instant expired = Instant.now().minusSeconds(1); b.setAssignmentExpiresAt(expired);
            assignments.findByBooking_IdOrderByCreatedAtDesc(first).getFirst().setExpiresAt(expired);
        });
        assertThat(race(() -> trips.confirmTrip(driverUser, String.valueOf(first)), optimization::processPendingAssignmentTimeouts))
                .containsExactly(false, true);
        assertThat(assignments.findByBooking_IdOrderByCreatedAtDesc(first))
                .anyMatch(a -> a.getStatus() == AssignmentStatus.TIMEOUT);
    }

    @Test
    void refundApproveAndProofCannotWriteTwice() throws Exception {
        String code = tx.execute(status -> {
            Booking b = bookings.findById(first).orElseThrow();
            var paid = new com.fptufa26.ecomotion.booking.entity.PaymentTransaction();
            paid.setBooking(b); paid.setAmount(new BigDecimal("100000"));
            paid.setType(com.fptufa26.ecomotion.booking.enums.PaymentType.DEPOSIT_PAY);
            paid.setDirection(com.fptufa26.ecomotion.booking.enums.PaymentDirection.IN);
            paid.setMethod(com.fptufa26.ecomotion.booking.enums.PaymentMethod.BANK_TRANSFER);
            paid.setStatus(com.fptufa26.ecomotion.booking.enums.PaymentStatus.PAID); ledger.save(paid);
            return b.getBookingCode();
        });
        var request = new com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest(code, new BigDecimal("10000"), "Test refund", null);
        long refund = refunds.createRefundRequest(providerUser, request).id();
        assertThat(refunds.createRefundRequest(providerUser, request).id()).isEqualTo(refund);
        var approve = new com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest("APPROVE", null);
        assertThat(race(() -> refunds.reviewRefundRequest(providerUser, refund, approve),
                () -> refunds.reviewRefundRequest(providerUser, refund, approve))).containsExactlyInAnyOrder(true, false);
        long transaction = refundRequests.findById(refund).orElseThrow().getPaymentTransaction().getId();
        var proof = new org.springframework.mock.web.MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4 TV5".getBytes());
        assertThat(race(() -> refunds.completeRefund(providerUser, transaction, proof, "TEST-REF", null),
                () -> refunds.completeRefund(providerUser, transaction, proof, "TEST-REF", null))).containsExactlyInAnyOrder(true, false);
        assertThat(refundRequests.findById(refund).orElseThrow().getStatus())
                .isEqualTo(com.fptufa26.ecomotion.finance.enums.RefundStatus.REFUNDED);
        assertThat(ledger.findByBooking_Id(first)).filteredOn(t -> t.getType() == com.fptufa26.ecomotion.booking.enums.PaymentType.REFUND).hasSize(1);
    }

    @Test
    void queueDateFilterAndExportUseSameVietnamDayAndStableSort() {
        tx.executeWithoutResult(status -> {
            bookings.findByIdForUpdate(first).orElseThrow().setPickupTime(Instant.parse("2026-10-28T18:00:00Z"));
            bookings.findByIdForUpdate(second).orElseThrow().setPickupTime(Instant.parse("2026-10-29T17:30:00Z"));
        });
        var filter = new com.fptufa26.ecomotion.booking.dto.request.BookingQueueFilter("ALL", null, null, null,
                "2026-10-29", "2026-10-29", "NEWEST");
        var page = providerBookings.filterBookings(providerUser, filter, 0, 1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse::bookingId).containsExactly(first);
        String csv = new String(providerBookings.exportFilteredBookings(providerUser, filter), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains(bookings.findById(first).orElseThrow().getBookingCode());
        assertThat(csv).doesNotContain(bookings.findById(second).orElseThrow().getBookingCode());
        var all = new com.fptufa26.ecomotion.booking.dto.request.BookingQueueFilter("ALL", null, null, null, null, null, "NEWEST");
        var rows = providerBookings.filterBookings(providerUser, all, 0, 10).items();
        assertThat(rows).hasSize(2);
        for (int i = 1; i < rows.size(); i++) {
            var previous = rows.get(i - 1); var next = rows.get(i);
            assertThat(previous.updatedAt().compareTo(next.updatedAt()) > 0
                    || (previous.updatedAt().equals(next.updatedAt()) && previous.bookingId() > next.bookingId())).isTrue();
        }
    }

    String bearer(long userId) {
        return "Bearer " + tx.execute(status -> jwt.generateAccessToken(em.find(User.class, userId)));
    }

    @Test
    void incidentContractAndProviderOwnershipAreEnforcedOverHttp() throws Exception {
        long[] incidents = tx.execute(status -> {
            Booking booking = bookings.findById(first).orElseThrow();
            long[] ids = new long[2];
            for (int i = 0; i < 2; i++) {
                var incident = new com.fptufa26.ecomotion.booking.entity.TripIncident();
                incident.setBooking(booking); incident.setDriver(em.find(Driver.class, driver));
                incident.setCreatedBy(em.find(User.class, driverUser)); incident.setDescription("Test");
                incident.setIncidentType(com.fptufa26.ecomotion.booking.enums.TripIncidentType.TRAFFIC_JAM);
                em.persist(incident); ids[i] = incident.getId();
            }
            return ids;
        });
        String url = "/api/provider/bookings/" + first + "/incidents/" + incidents[0] + "/resolve";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", bearer(providerUser)).contentType("application/json")
                .content("{\"resolutionNotes\":\"Test\",\"newStatus\":\"RESOLVED\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", bearer(driverUser)).contentType("application/json")
                .content("{\"resolutionNote\":\"Test\",\"status\":\"RESOLVED\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", bearer(providerUser)).contentType("application/json")
                .content("{\"resolutionNote\":\"  Resolved  \",\"status\":\"RESOLVED\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        tx.executeWithoutResult(status -> {
            assertThat(em.find(com.fptufa26.ecomotion.booking.entity.TripIncident.class, incidents[0]).getResolutionNote()).isEqualTo("Resolved");
            assertThat(em.find(com.fptufa26.ecomotion.booking.entity.TripIncident.class, incidents[1]).getStatus())
                    .isEqualTo(com.fptufa26.ecomotion.booking.enums.TripIncidentStatus.OPEN);
        });
    }

    @Test
    void surchargeRecalculatesMoneyAndRepeatedRequestDoesNotDuplicate() {
        tx.executeWithoutResult(status -> {
            Booking booking = bookings.findByIdForUpdate(first).orElseThrow();
            booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION); booking.setActualEndAt(Instant.now());
            var policy = em.createQuery("select p from BookingPolicySnapshot p where p.booking.id = :id",
                    com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot.class).setParameter("id", booking.getId()).getSingleResult();
            policy.setOvertimeSurchargeRatio(new BigDecimal("0.2"));
        });
        var request = new com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest(
                com.fptufa26.ecomotion.booking.enums.ChargeType.OVERTIME, "Overtime evidence", 2,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, "https://example.test/proof.png");
        var charge = providerBookings.addCharge(providerUser, bookings.findById(first).orElseThrow().getBookingCode(), request);
        assertThat(charge.amount()).isEqualByComparingTo("240000");
        assertThat(providerBookings.addCharge(providerUser, bookings.findById(first).orElseThrow().getBookingCode(), request).id()).isEqualTo(charge.id());
        assertThat(providerBookings.getCharges(providerUser, bookings.findById(first).orElseThrow().getBookingCode()))
                .filteredOn(c -> c.chargeType() == com.fptufa26.ecomotion.booking.enums.ChargeType.OVERTIME).hasSize(1);
    }

    @Test
    void refundReviewRejectsWrongDtoAndRequiresRejectionReason() throws Exception {
        long refund = tx.execute(status -> {
            var request = new com.fptufa26.ecomotion.finance.entity.RefundRequest();
            request.setBooking(bookings.findById(first).orElseThrow()); request.setRequestedBy(em.find(User.class, renterUser));
            request.setAmount(new BigDecimal("1000")); request.setReason("Test");
            request.setStatus(com.fptufa26.ecomotion.finance.enums.RefundStatus.REQUESTED); em.persist(request);
            return request.getId();
        });
        String url = "/api/v1/provider/finance/refunds/" + refund + "/review";
        for (String body : List.of("{\"approved\":false,\"note\":\"Test\"}", "{\"action\":\"REJECT\",\"rejectionReason\":\" \"}")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                    .header("Authorization", bearer(providerUser)).contentType("application/json").content(body))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", bearer(providerUser)).contentType("application/json")
                .content("{\"action\":\"REJECT\",\"rejectionReason\":\"  Missing proof  \"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("REJECTED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.rejectionReason").value("Missing proof"));
    }

    @Test
    void repeatedIpnIsSerializedAndCallbackDoesNotConfirmMoney() throws Exception {
        String ref = UUID.randomUUID().toString();
        long transaction = tx.execute(status -> {
            Booking b = bookings.findByIdForUpdate(first).orElseThrow(); b.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
            var payment = new com.fptufa26.ecomotion.booking.entity.PaymentTransaction(); payment.setBooking(b);
            payment.setAmount(new BigDecimal("100000")); payment.setVnpTxnRef(ref);
            payment.setType(com.fptufa26.ecomotion.booking.enums.PaymentType.SETTLEMENT);
            payment.setDirection(com.fptufa26.ecomotion.booking.enums.PaymentDirection.IN);
            payment.setMethod(com.fptufa26.ecomotion.booking.enums.PaymentMethod.VNPAY);
            payment.setStatus(com.fptufa26.ecomotion.booking.enums.PaymentStatus.PENDING); return ledger.save(payment).getId();
        });
        var gateway = org.mockito.Mockito.mock(com.fptufa26.ecomotion.finance.service.gateway.PaymentGatewayStrategy.class);
        org.mockito.Mockito.when(gatewayResolver.resolve(com.fptufa26.ecomotion.booking.enums.PaymentMethod.VNPAY)).thenReturn(gateway);
        var verified = new com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentVerificationResult(
                true, true, ref, "TV5-TEST", "00", new BigDecimal("100000"), "NCB", "ATM", "20261029", "Test");
        org.mockito.Mockito.when(gateway.verifyIpn(org.mockito.ArgumentMatchers.anyMap())).thenReturn(verified);
        org.mockito.Mockito.when(gateway.verifyCallback(org.mockito.ArgumentMatchers.anyMap())).thenReturn(verified);
        var params = java.util.Map.of("vnp_TxnRef", ref, "vnp_Amount", "10000000");
        vnpay.processCallback(params);
        assertThat(ledger.findById(transaction).orElseThrow().getStatus()).isEqualTo(com.fptufa26.ecomotion.booking.enums.PaymentStatus.PENDING);
        assertThat(race(() -> vnpay.processIpn(params), () -> vnpay.processIpn(params))).containsOnly(true);
        assertThat(ledger.findById(transaction).orElseThrow().getStatus()).isEqualTo(com.fptufa26.ecomotion.booking.enums.PaymentStatus.PAID);
        assertThat(bookings.findById(first).orElseThrow().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(vnpay.processIpn(params).rspCode()).isEqualTo("02");
        assertThat(ledger.findByBooking_Id(first)).hasSize(1);
    }

    @Test
    void providerAssignmentUsesCapabilitiesDeadlineAndReturnsActualSolverStatus() {
        String code = bookings.findById(first).orElseThrow().getBookingCode();
        assertThat(providerBookings.getBookingDetail(providerUser, code).canAssignDriver()).isTrue();
        providerBookings.assignDriver(providerUser, code, driver);
        assertThat(providerBookings.getBookingDetail(providerUser, code).canAssignDriver()).isFalse();
        assertThat(providerBookings.getBookingDetail(providerUser, code).canAutoAssignDriver()).isTrue();
        var auto = providerBookings.autoAssignDriver(providerUser, code);
        assertThat(auto.solverStatus()).isEqualTo("ALREADY_ASSIGNED");
        assertThat(auto.booking().status()).isEqualTo(BookingStatus.DRIVER_ASSIGNED);
        tx.executeWithoutResult(status -> {
            Booking b = bookings.findById(first).orElseThrow();
            var current = optimization.requireCurrentAssignment(b, driver);
            assertThat(current.getExpiresAt()).isEqualTo(b.getAssignmentExpiresAt());
            assertThat(java.time.Duration.between(b.getAssignedAt(), current.getExpiresAt()).toMinutes()).isEqualTo(15);
        });
        assertThat(assignments.findByBooking_IdOrderByCreatedAtDesc(first)).hasSize(1);
    }

    @Test
    void replacementRecallsOldAssignmentAndRejectsOldDriver() {
        optimization.manualAssign(first, driver, provider);
        long[] replacement = tx.execute(status -> {
            Driver d = new Driver(); d.setUser(user(Role.DRIVER)); d.setProvider(em.find(Provider.class, provider));
            d.setLicenseNo(UUID.randomUUID().toString().substring(0, 18)); d.setLicenseClass("B2");
            d.setLicenseExpiry(LocalDate.now().plusYears(5)); d.setPortraitUrl("test"); d.setLicenseImageUrl("test");
            d.setStatus(DriverStatus.APPROVED); em.persist(d);
            Booking b = bookings.findByIdForUpdate(first).orElseThrow(); b.setDriverReplacementRequired(true);
            return new long[]{d.getId(), d.getUser().getId()};
        });
        String code = bookings.findById(first).orElseThrow().getBookingCode();
        providerBookings.assignDriver(providerUser, code, replacement[0]);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> trips.confirmTrip(driverUser, code))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        trips.confirmTrip(replacement[1], code);
        assertThat(assignments.findByBooking_IdOrderByCreatedAtDesc(first))
                .filteredOn(a -> a.getStatus() == AssignmentStatus.PENDING_RESPONSE).isEmpty();
        assertThat(assignments.findByBooking_IdOrderByCreatedAtDesc(first))
                .filteredOn(a -> a.getStatus() == AssignmentStatus.RECALLED).hasSize(1);
    }

    @Test
    void futureWorkloadAndMissingLocationAreExplicitAndOfflineCannotConfirm() {
        optimization.manualAssign(first, driver, provider);
        var candidate = optimization.evaluateCandidates(first, provider).stream().filter(c -> c.driverId() == driver).findFirst().orElseThrow();
        assertThat(candidate.activeTripsCount()).isEqualTo(1L);
        assertThat(candidate.etaMinutes()).isNull(); assertThat(candidate.distanceKm()).isNull();
        tx.executeWithoutResult(status -> em.find(Driver.class, driver).setActivityStatus(DriverActivityStatus.OFFLINE));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> trips.confirmTrip(driverUser, String.valueOf(first)))
                .isInstanceOf(com.fptufa26.ecomotion.booking.exception.TripConflictException.class);
    }

    @Test
    void staleGpsIsNotPresentedAsLiveEta() {
        long location = tx.execute(status -> {
            Booking booking = bookings.findByIdForUpdate(first).orElseThrow();
            booking.setPickupLatitude(new BigDecimal("10.0")); booking.setPickupLongitude(new BigDecimal("106.0"));
            var point = new com.fptufa26.ecomotion.booking.entity.VehicleLocation();
            point.setBooking(booking); point.setVehicle(booking.getVehicle()); point.setDriver(em.find(Driver.class, driver));
            point.setLatitude(10.0); point.setLongitude(106.0);
            point.setReceivedAt(Instant.now().minusSeconds(600)); point.setRecordedAt(Instant.now().minusSeconds(600));
            em.persist(point); return point.getId();
        });
        var old = optimization.evaluateCandidates(first, provider).stream().filter(c -> c.driverId() == driver).findFirst().orElseThrow();
        assertThat(old.etaMinutes()).isNull();
        tx.executeWithoutResult(status -> {
            var point = em.find(com.fptufa26.ecomotion.booking.entity.VehicleLocation.class, location);
            point.setReceivedAt(Instant.now()); point.setRecordedAt(Instant.now().minusMillis(1));
        });
        var fresh = optimization.evaluateCandidates(first, provider).stream().filter(c -> c.driverId() == driver).findFirst().orElseThrow();
        assertThat(fresh.etaMinutes()).isEqualTo(5);
        assertThat(fresh.distanceKm()).isEqualTo(0.0);
    }

    @Test void concurrentRefundRequestsCannotReserveMoreThanCollectedMoney() throws Exception {
        tx.executeWithoutResult(status -> {
            var payment = new com.fptufa26.ecomotion.booking.entity.PaymentTransaction();
            payment.setBooking(em.find(Booking.class, first)); payment.setAmount(new BigDecimal("100000"));
            payment.setType(com.fptufa26.ecomotion.booking.enums.PaymentType.DEPOSIT_PAY);
            payment.setDirection(com.fptufa26.ecomotion.booking.enums.PaymentDirection.IN);
            payment.setMethod(com.fptufa26.ecomotion.booking.enums.PaymentMethod.BANK_TRANSFER);
            payment.setStatus(com.fptufa26.ecomotion.booking.enums.PaymentStatus.PAID); ledger.save(payment);
        });
        String code = bookings.findById(first).orElseThrow().getBookingCode();
        assertThat(race(() -> refunds.createRefundRequest(renterUser, new com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest(code, new BigDecimal("70000"), "First", null)),
                () -> refunds.createRefundRequest(renterUser, new com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest(code, new BigDecimal("70000"), "Second", null))))
                .containsExactlyInAnyOrder(true, false);
    }

    @Test void uniqueBankReferenceProtectsConcurrentRefundsAcrossDifferentBookings() throws Exception {
        long[] ids = tx.execute(status -> java.util.stream.LongStream.of(first, second).map(id -> {
            var incoming = new com.fptufa26.ecomotion.booking.entity.PaymentTransaction();
            incoming.setBooking(em.find(Booking.class, id)); incoming.setAmount(new BigDecimal("10000"));
            incoming.setType(com.fptufa26.ecomotion.booking.enums.PaymentType.DEPOSIT_PAY);
            incoming.setDirection(com.fptufa26.ecomotion.booking.enums.PaymentDirection.IN);
            incoming.setMethod(com.fptufa26.ecomotion.booking.enums.PaymentMethod.BANK_TRANSFER);
            incoming.setStatus(com.fptufa26.ecomotion.booking.enums.PaymentStatus.PAID); ledger.save(incoming);
            var payment = new com.fptufa26.ecomotion.booking.entity.PaymentTransaction();
            payment.setBooking(em.find(Booking.class, id)); payment.setAmount(new BigDecimal("10000"));
            payment.setType(com.fptufa26.ecomotion.booking.enums.PaymentType.REFUND);
            payment.setDirection(com.fptufa26.ecomotion.booking.enums.PaymentDirection.OUT);
            payment.setMethod(com.fptufa26.ecomotion.booking.enums.PaymentMethod.BANK_TRANSFER);
            payment.setStatus(com.fptufa26.ecomotion.booking.enums.PaymentStatus.PENDING); return ledger.save(payment).getId();
        }).toArray());
        var proof = new org.springframework.mock.web.MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4 TV5".getBytes());
        String reference = "CROSS-BOOKING-" + UUID.randomUUID();
        assertThat(race(() -> refunds.completeRefund(providerUser, ids[0], proof, reference, null),
                () -> refunds.completeRefund(providerUser, ids[1], proof, reference, null))).containsExactlyInAnyOrder(true, false);
        assertThat(ledger.findAllById(List.of(ids[0], ids[1]))).filteredOn(t -> t.getStatus() == com.fptufa26.ecomotion.booking.enums.PaymentStatus.PAID).hasSize(1);
    }

}
