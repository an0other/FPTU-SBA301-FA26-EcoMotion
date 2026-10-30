package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.finance.dto.request.*;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.*;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"app.finance.sandbox-payout-enabled=true", "logging.level.org.hibernate.SQL=WARN"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
class Tv5FinancialSafetyIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired RefundService refunds;
    @Autowired ProviderFinanceService finance;
    @Autowired VnpayService vnpay;
    @Autowired com.fptufa26.ecomotion.booking.service.DriverTripService trips;
    @Autowired com.fptufa26.ecomotion.booking.service.BookingService renterBookings;
    @Autowired com.fptufa26.ecomotion.booking.service.ProviderBookingService providerBookings;
    @Autowired com.fptufa26.ecomotion.booking.service.BookingWorkflowService workflow;
    @Autowired com.fptufa26.ecomotion.booking.service.DriverOptimizationService optimization;
    @Autowired com.fptufa26.ecomotion.schedule.service.ScheduleService schedule;
    @Autowired PaymentTransactionRepository ledger;
    @Autowired BookingRepository bookings;
    @Autowired RefundRequestRepository requests;
    @Autowired BookingPolicySnapshotRepository snapshots;
    @Autowired com.fptufa26.ecomotion.finance.config.VnpayConfig config;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fptufa26.ecomotion.common.security.JwtService jwt;
    TransactionTemplate tx;
    long bookingId;
    long renterId;
    long providerId;
    long adminId;

    User user(Role role) {
        User u = new User(); u.setEmail(UUID.randomUUID() + "@safety.test"); u.setPasswordHash("test-only");
        u.setFullName("TV5 safety"); u.setRole(role); u.setStatus(UserStatus.ACTIVE); em.persist(u); return u;
    }

    @BeforeEach void fixture() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            Provider p = new Provider(); p.setUser(user(Role.PROVIDER)); p.setProviderType(ProviderType.INDIVIDUAL);
            p.setDisplayName("TV5"); p.setHotline("0900000000"); p.setDepotAddress("Test");
            p.setCommissionRate(new BigDecimal("0.1")); p.setDepositRatio(new BigDecimal("0.3"));
            p.setStatus(ProviderStatus.APPROVED); em.persist(p); providerId = p.getUser().getId();
            Renter r = new Renter(); r.setUser(user(Role.RENTER)); em.persist(r); renterId = r.getUser().getId();
            adminId = user(Role.ADMIN).getId();
            Vehicle v = new Vehicle(); v.setProvider(p); v.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            v.setChassisNo(UUID.randomUUID().toString()); v.setEngineNo(UUID.randomUUID().toString());
            v.setBrand("Test"); v.setModel("TV5"); v.setManufactureYear(2026); v.setSeatCount(4);
            v.setTransmission(Transmission.AUTOMATIC); v.setColor("White"); v.setFuelType(FuelType.EV);
            v.setMaintenanceBufferMinutes(0); v.setStatus(VehicleStatus.APPROVED); em.persist(v);
            RentalPlan plan = new RentalPlan(); plan.setVehicle(v); plan.setPlanType(PlanType.HOURLY);
            plan.setSlotLengthMinutes(60); plan.setSlotPrice(new BigDecimal("100000")); em.persist(plan);
            Booking b = new Booking(); b.setBookingCode(UUID.randomUUID().toString().substring(0, 30).toUpperCase(java.util.Locale.ROOT));
            b.setRenter(r); b.setVehicle(v); b.setPlan(plan); b.setStatus(BookingStatus.COMPLETED);
            b.setPickupTime(Instant.now().plusSeconds(86400)); b.setPickupAddress("Test pickup");
            b.setPassengerCount(1); b.setTotalAmount(new BigDecimal("100000")); em.persist(b); bookingId = b.getId();
            BookingPolicySnapshot snapshot = new BookingPolicySnapshot(); snapshot.setBooking(b);
            snapshot.setPlanType(PlanType.HOURLY); snapshot.setSlotLengthMinutes(60);
            snapshot.setSlotPrice(new BigDecimal("100000")); snapshot.setDepositRatio(new BigDecimal("0.3")); em.persist(snapshot);
            ProviderBankAccount bank = new ProviderBankAccount(); bank.setProvider(p); bank.setBankCode("VCB");
            bank.setBankName("Test bank"); bank.setAccountNumber("0123456789"); bank.setAccountHolderName("TEST OWNER");
            bank.setStatus(BankAccountStatus.ACTIVE); em.persist(bank);
            incoming(b, PaymentMethod.VNPAY, new BigDecimal("100000"));
        });
    }

    PaymentTransaction incoming(Booking b, PaymentMethod method, BigDecimal amount) {
        PaymentTransaction t = new PaymentTransaction(); t.setBooking(b); t.setType(PaymentType.DEPOSIT_PAY);
        t.setDirection(PaymentDirection.IN); t.setMethod(method); t.setAmount(amount); t.setStatus(PaymentStatus.PAID);
        t.setVerificationMethod(method == PaymentMethod.VNPAY ? PaymentVerificationMethod.GATEWAY_VERIFIED : PaymentVerificationMethod.PROVIDER_CONFIRMED);
        em.persist(t); return t;
    }

    CreateRefundRequest request(String amount, String reason) {
        return new CreateRefundRequest(bookings.findById(bookingId).orElseThrow().getBookingCode(), new BigDecimal(amount), reason, null);
    }

    List<Boolean> race(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = List.of(first, second).stream().map(task -> pool.submit(() -> {
                start.await();
                try { task.run(); return true; }
                catch (com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException
                       | com.fptufa26.ecomotion.common.exception.ConflictException ex) { return false; }
            })).toList();
            start.countDown(); return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentRequestsReserveMoneyBeforeApproval() throws Exception {
        var first = request("70000", "first"); var second = request("70000", "second");
        assertThat(race(() -> refunds.createRefundRequest(renterId, first), () -> refunds.createRefundRequest(renterId, second)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(requests.sumCommittedRefundsForBooking(bookingId)).isEqualByComparingTo("70000");
    }

    @Test void pendingRefundCountsItselfOnlyOnceAtApprovalAndDuplicateApprovalCannotPayTwice() throws Exception {
        long id = refunds.createRefundRequest(renterId, request("100000", "full")).id();
        var approve = new ReviewRefundRequest("APPROVE", null);
        assertThat(race(() -> refunds.reviewRefundRequest(providerId, id, approve), () -> refunds.reviewRefundRequest(adminId, id, approve)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(ledger.findByBooking_Id(bookingId).stream().filter(t -> t.getType() == PaymentType.REFUND)).hasSize(1);
    }

    @Test void repeatedRequestIsIdempotentAndOtherRequestedAmountCannotExceedLimit() {
        var input = request("70000", "same");
        assertThat(refunds.createRefundRequest(renterId, input).id()).isEqualTo(refunds.createRefundRequest(renterId, input).id());
        assertThatThrownBy(() -> refunds.createRefundRequest(renterId, request("40000", "other"))).isInstanceOf(RuntimeException.class);
    }

    @Test void unlinkedAutomaticRefundAlsoReservesCapacity() {
        tx.executeWithoutResult(status -> {
            PaymentTransaction t = incoming(em.find(Booking.class, bookingId), PaymentMethod.BANK_TRANSFER, new BigDecimal("80000"));
            t.setType(PaymentType.REFUND); t.setDirection(PaymentDirection.OUT); t.setStatus(PaymentStatus.PENDING);
        });
        assertThatThrownBy(() -> refunds.createRefundRequest(renterId, request("30000", "other"))).isInstanceOf(RuntimeException.class);
    }

    @Test void zeroAndNegativeRefundsAreRejectedByService() {
        for (String amount : List.of("0", "-1000")) {
            assertThatThrownBy(() -> refunds.createRefundRequest(renterId, request(amount, "invalid"))).isInstanceOf(RuntimeException.class);
        }
    }

    @Test void proofCannotCompleteLegacyRefundLedgerAboveActualCollections() {
        long refund = tx.execute(status -> {
            PaymentTransaction t = incoming(em.find(Booking.class, bookingId), PaymentMethod.BANK_TRANSFER, new BigDecimal("120000"));
            t.setDirection(PaymentDirection.OUT); t.setType(PaymentType.REFUND); t.setStatus(PaymentStatus.PENDING);
            return t.getId();
        });
        var proof = new MockMultipartFile("file", "refund.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThatThrownBy(() -> refunds.completeRefund(providerId, refund, proof, "OVER-LIMIT", "Invalid legacy refund"))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThat(ledger.findById(refund).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test void sandboxPayoutIsPersistentPendingAndIdempotent() throws Exception {
        var payout = finance.createSandboxPayout(adminId, bookingId);
        assertThat(payout.netAmount()).isEqualByComparingTo("90000");
        assertThat(payout.status()).isEqualTo(PaymentStatus.PENDING); assertThat(payout.paidAt()).isNull();
        assertThat(payout.verificationMethod()).isEqualTo(PaymentVerificationMethod.MOCK_SIMULATED);
        assertThat(finance.createSandboxPayout(adminId, bookingId).id()).isEqualTo(payout.id());
        assertThat(finance.listPayouts(providerId, null, 0, 10).items()).extracting("id").contains(payout.id());
        var admin = tx.execute(s -> em.find(User.class, adminId));
        String token = jwt.generateAccessToken(admin);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/provider/finance/sandbox/payouts/" + bookingId).header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test void directProviderMoneyCannotBePaidOutByPlatform() {
        tx.executeWithoutResult(status -> ledger.findByBooking_Id(bookingId).forEach(t -> {
            t.setMethod(PaymentMethod.BANK_TRANSFER); t.setVerificationMethod(PaymentVerificationMethod.PROVIDER_CONFIRMED);
        }));
        assertThatThrownBy(() -> finance.createSandboxPayout(adminId, bookingId)).isInstanceOf(RuntimeException.class);
        assertThat(finance.getFinanceSummary(providerId).directReceived()).isEqualByComparingTo("100000");
        assertThat(finance.getFinanceSummary(providerId).platformReceived()).isZero();
    }

    @Test void providerAndUnsettledBookingCannotCreatePayout() {
        assertThatThrownBy(() -> finance.createSandboxPayout(providerId, bookingId)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        tx.executeWithoutResult(status -> em.find(Booking.class, bookingId).setStatus(BookingStatus.AWAITING_FINAL_PAYMENT));
        assertThatThrownBy(() -> finance.createSandboxPayout(adminId, bookingId)).isInstanceOf(RuntimeException.class);
    }

    @Test void outstandingRefundBlocksPayout() {
        refunds.createRefundRequest(renterId, request("10000", "refund"));
        assertThatThrownBy(() -> finance.createSandboxPayout(adminId, bookingId)).isInstanceOf(RuntimeException.class);
    }

    @Test void mixedCollectionsOnlyAllocatePlatformMoneyAndKeepFeeSnapshot() {
        tx.executeWithoutResult(status -> {
            ledger.findByBooking_Id(bookingId).forEach(t -> t.setAmount(new BigDecimal("30000")));
            incoming(em.find(Booking.class, bookingId), PaymentMethod.BANK_TRANSFER, new BigDecimal("70000"));
        });
        var payout = finance.createSandboxPayout(adminId, bookingId);
        assertThat(payout.grossAmount()).isEqualByComparingTo("30000");
        assertThat(payout.platformFee()).isEqualByComparingTo("3000");
        assertThat(payout.netAmount()).isEqualByComparingTo("27000");
        tx.executeWithoutResult(status -> em.find(Booking.class, bookingId).getVehicle().getProvider().setCommissionRate(new BigDecimal("0.5")));
        assertThat(finance.listPayouts(providerId, null, 0, 10).items().getFirst().platformFee()).isEqualByComparingTo("3000");
    }

    @Test void mixedSourceRefundRequiresSourceReconciliationBeforePayout() {
        tx.executeWithoutResult(status -> {
            ledger.findByBooking_Id(bookingId).forEach(t -> t.setAmount(new BigDecimal("30000")));
            Booking booking = em.find(Booking.class, bookingId);
            incoming(booking, PaymentMethod.BANK_TRANSFER, new BigDecimal("70000"));
            PaymentTransaction refund = incoming(booking, PaymentMethod.BANK_TRANSFER, new BigDecimal("10000"));
            refund.setType(PaymentType.REFUND); refund.setDirection(PaymentDirection.OUT);
        });
        assertThatThrownBy(() -> finance.createSandboxPayout(adminId, bookingId))
                .isInstanceOf(com.fptufa26.ecomotion.finance.exception.PayoutIneligibleException.class)
                .hasMessageContaining("nguồn");
    }

    @Test void cancelledBookingRecordsVerifiedMoneyAndRequiresReconciliationWithoutRetry() {
        var session = tx.execute(status -> {
            Booking b = em.find(Booking.class, bookingId); b.setStatus(BookingStatus.ACCEPTED);
            ledger.deleteAll(ledger.findByBooking_Id(bookingId)); em.flush();
            return vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(b.getBookingCode(), "DEPOSIT", null, "vn"), "127.0.0.1");
        });
        tx.executeWithoutResult(status -> em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLED));
        var params = new java.util.HashMap<String, String>();
        params.put("vnp_TmnCode", config.getTmnCode()); params.put("vnp_TxnRef", session.vnpTxnRef());
        params.put("vnp_Amount", session.amount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_ResponseCode", "00"); params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", com.fptufa26.ecomotion.finance.config.VnpayUtils.hmacSHA512(config.getHashSecret(),
                com.fptufa26.ecomotion.finance.config.VnpayUtils.buildHashData(params)));
        assertThat(vnpay.processIpn(params).rspCode()).isEqualTo("00");
        PaymentTransaction paid = ledger.findByVnpTxnRef(session.vnpTxnRef()).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID); assertThat(paid.getAppliedAmount()).isZero();
        assertThat(vnpay.processIpn(params).rspCode()).isEqualTo("02");
        var result = vnpay.queryTransaction(session.bookingCode(), session.vnpTxnRef());
        assertThat(result.status()).isEqualTo("RECONCILIATION_REQUIRED"); assertThat(result.retryAllowed()).isFalse();
        assertThat(result.success()).isFalse(); assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test void duplicateBankReferenceCannotCompleteAnotherRefundAndOtherObligationsKeepCancellationInReview() {
        long first = refunds.createRefundRequest(renterId, request("30000", "first")).id();
        long second = refunds.createRefundRequest(renterId, request("30000", "second")).id();
        var approve = new ReviewRefundRequest("APPROVE", null);
        long firstTx = refunds.reviewRefundRequest(providerId, first, approve).paymentTransactionId();
        long secondTx = refunds.reviewRefundRequest(providerId, second, approve).paymentTransactionId();
        tx.executeWithoutResult(s -> em.find(Booking.class, bookingId).setStatus(BookingStatus.CANCELLATION_REVIEW));
        var proof = new MockMultipartFile("file", "refund.png", "image/png", new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a});
        String reference = "BANK-" + UUID.randomUUID();
        refunds.completeRefund(providerId, firstTx, proof, reference, "Manually verified by provider");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        assertThatThrownBy(() -> refunds.completeRefund(providerId, secondTx, proof, reference.toLowerCase(), "Duplicate"))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThat(ledger.findById(secondTx).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test void pendingProviderAcceptanceCannotCreateDepositPayment() {
        tx.executeWithoutResult(s -> {
            ledger.deleteAll(ledger.findByBooking_Id(bookingId));
            em.find(Booking.class, bookingId).setStatus(BookingStatus.PENDING);
        });
        assertThatThrownBy(() -> vnpay.createPaymentUrl(renterId,
                new CreateVnpayPaymentUrlRequest(bookings.findById(bookingId).orElseThrow().getBookingCode(), "DEPOSIT", null, "vn"), "127.0.0.1"))
                .isInstanceOf(com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException.class);
    }

    java.util.Map<String, String> signed(com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse session) {
        var params = new java.util.HashMap<String, String>();
        params.put("vnp_TmnCode", config.getTmnCode()); params.put("vnp_TxnRef", session.vnpTxnRef());
        params.put("vnp_Amount", session.amount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_ResponseCode", "00"); params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", com.fptufa26.ecomotion.finance.config.VnpayUtils.hmacSHA512(config.getHashSecret(),
                com.fptufa26.ecomotion.finance.config.VnpayUtils.buildHashData(params)));
        return params;
    }

    @Test void expiredHoldKeepsMoneyPaidWithoutConfirmingBooking() {
        var session = tx.execute(status -> {
            Booking b = em.find(Booking.class, bookingId); ledger.deleteAll(ledger.findByBooking_Id(bookingId));
            b.setStatus(BookingStatus.AWAITING_DEPOSIT); em.flush();
            var hold = new com.fptufa26.ecomotion.schedule.entity.VehicleSchedule(); hold.setVehicle(b.getVehicle()); hold.setBooking(b);
            hold.setType(com.fptufa26.ecomotion.schedule.enums.ScheduleType.BOOKED);
            hold.setStatus(com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.HELD);
            hold.setStartAt(b.getPickupTime()); hold.setEndAt(b.getPickupTime().plusSeconds(3600));
            hold.setExpiresAt(Instant.now().plusSeconds(60)); em.persist(hold);
            return vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(b.getBookingCode(), "DEPOSIT", null, "vn"), "127.0.0.1");
        });
        tx.executeWithoutResult(status -> em.createQuery("select s from VehicleSchedule s where s.booking.id = :id",
                        com.fptufa26.ecomotion.schedule.entity.VehicleSchedule.class).setParameter("id", bookingId)
                .getResultList().forEach(s -> s.setExpiresAt(Instant.now().minusSeconds(1))));
        assertThat(vnpay.processIpn(signed(session)).rspCode()).isEqualTo("00");
        assertThat(ledger.findByVnpTxnRef(session.vnpTxnRef()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLATION_REVIEW);
        assertThat(vnpay.queryTransaction(session.bookingCode(), session.vnpTxnRef()).status()).isEqualTo("RECONCILIATION_REQUIRED");
    }

    @Test void disputeOpenedAfterCheckoutPreventsCompletionButDoesNotLoseVerifiedMoney() {
        var session = tx.execute(status -> {
            Booking b = em.find(Booking.class, bookingId); ledger.deleteAll(ledger.findByBooking_Id(bookingId));
            b.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT); b.setActualEndAt(Instant.now()); em.flush();
            return vnpay.createPaymentUrl(renterId, new CreateVnpayPaymentUrlRequest(b.getBookingCode(), "FINAL_PAYMENT", null, "vn"), "127.0.0.1");
        });
        tx.executeWithoutResult(status -> {
            var dispute = new com.fptufa26.ecomotion.dispute.entity.Dispute(); dispute.setBooking(em.find(Booking.class, bookingId));
            dispute.setRaisedBy(em.find(User.class, renterId)); dispute.setCategory(com.fptufa26.ecomotion.dispute.enums.DisputeCategory.CHARGE);
            dispute.setStatus(com.fptufa26.ecomotion.dispute.enums.DisputeStatus.IN_REVIEW); em.persist(dispute);
        });
        assertThat(vnpay.processIpn(signed(session)).rspCode()).isEqualTo("00");
        assertThat(ledger.findByVnpTxnRef(session.vnpTxnRef()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.AWAITING_FINAL_PAYMENT);
        assertThat(vnpay.queryTransaction(session.bookingCode(), session.vnpTxnRef()).status()).isEqualTo("RECONCILIATION_REQUIRED");
    }

    @Test void actualEndRequiresBothPartiesAndRepeatedFinishKeepsTheOriginalConfirmation() {
        long driverUser = tx.execute(status -> {
            Booking b = em.find(Booking.class, bookingId);
            Driver d = new Driver(); d.setUser(user(Role.DRIVER)); d.setProvider(b.getVehicle().getProvider());
            d.setStatus(DriverStatus.APPROVED); d.setLicenseNo(UUID.randomUUID().toString().substring(0, 18));
            d.setLicenseClass("B2"); d.setLicenseExpiry(java.time.LocalDate.now().plusYears(1));
            d.setPortraitUrl("test"); d.setLicenseImageUrl("test"); em.persist(d);
            b.setDriver(d); b.setStatus(BookingStatus.IN_PROGRESS); b.setDriverConfirmedAt(Instant.now().minusSeconds(3600));
            b.setActualStartAt(Instant.now().minusSeconds(3500)); return d.getUser().getId();
        });
        String code = bookings.findById(bookingId).orElseThrow().getBookingCode();
        trips.finishTrip(driverUser, code);
        Booking waiting = bookings.findById(bookingId).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(BookingStatus.AWAITING_END_CONFIRMATION);
        assertThat(waiting.getActualEndAt()).isNull();
        Instant confirmation = waiting.getDriverEndConfirmedAt();
        trips.finishTrip(driverUser, code);
        assertThat(bookings.findById(bookingId).orElseThrow().getDriverEndConfirmedAt()).isEqualTo(confirmation);
        renterBookings.confirmEnd(renterId, code);
        Booking ended = bookings.findById(bookingId).orElseThrow();
        assertThat(ended.getStatus()).isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        assertThat(ended.getActualEndAt()).isNotNull();
    }

    @Test void vehicleChangeRevalidatesAssignedDriversLicenseAndRollsBackIncompatibleReplacement() {
        long[] ids = tx.execute(status -> {
            Booking b = em.find(Booking.class, bookingId); b.setStatus(BookingStatus.CONFIRMED);
            Driver d = new Driver(); d.setUser(user(Role.DRIVER)); d.setProvider(b.getVehicle().getProvider());
            d.setStatus(DriverStatus.APPROVED); d.setLicenseNo(UUID.randomUUID().toString().substring(0, 18));
            d.setLicenseClass("B2"); d.setLicenseExpiry(java.time.LocalDate.now().plusYears(1));
            d.setPortraitUrl("test"); d.setLicenseImageUrl("test"); em.persist(d);
            Vehicle bus = new Vehicle(); bus.setProvider(b.getVehicle().getProvider()); bus.setLicensePlate(UUID.randomUUID().toString().substring(0, 15));
            bus.setChassisNo(UUID.randomUUID().toString()); bus.setEngineNo(UUID.randomUUID().toString());
            bus.setBrand("Test"); bus.setModel("Bus"); bus.setManufactureYear(2026); bus.setSeatCount(45);
            bus.setTransmission(Transmission.MANUAL); bus.setColor("White"); bus.setFuelType(FuelType.DIESEL);
            bus.setMaintenanceBufferMinutes(0); bus.setStatus(VehicleStatus.APPROVED); em.persist(bus);
            schedule.book(b.getVehicle(), b, b.getPickupTime(), b.getPickupTime().plusSeconds(3600));
            return new long[]{d.getId(), bus.getId(), b.getVehicle().getId(), b.getVehicle().getProvider().getId()};
        });
        optimization.manualAssign(bookingId, ids[0], ids[3]);
        String code = bookings.findById(bookingId).orElseThrow().getBookingCode();
        workflow.proposeAlternative(providerId, code, new com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest(
                OperationalResolutionType.CHANGE_VEHICLE, ids[1], null, "Vehicle replacement"));
        String proposalId = workflow.getWorkflow(renterId, code).proposal().id();
        assertThatThrownBy(() -> workflow.respondToProposal(renterId, code, proposalId,
                new com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest("ACCEPT", "Agreed")))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ConflictException.class);
        assertThat(workflow.getWorkflow(renterId, code).proposal().status()).isEqualTo("PENDING");
        assertThat(tx.<Long>execute(status -> em.find(Booking.class, bookingId).getVehicle().getId())).isEqualTo(ids[2]);
    }
}
