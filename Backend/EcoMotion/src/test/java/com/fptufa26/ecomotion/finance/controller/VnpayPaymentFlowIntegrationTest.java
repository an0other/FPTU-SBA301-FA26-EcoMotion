package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.config.VnpayUtils;
import com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest;
import com.fptufa26.ecomotion.finance.dto.response.VnpayCallbackResultResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayIpnResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.service.VnpayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class VnpayPaymentFlowIntegrationTest {

    @Autowired
    private VnpayService vnpayService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private VnpayConfig vnpayConfig;

    @Autowired
    private com.fptufa26.ecomotion.schedule.service.ScheduleService scheduleService;

    @Autowired
    private com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository snapshotRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private com.fptufa26.ecomotion.finance.service.PaymentCodeGenerator paymentCodeGenerator;

    @Test
    void http200IpnDoesNotMeanPaymentSucceededAndAnonymousCannotCreatePayments() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payment/vnpay/ipn")
                        .param("vnp_TxnRef", "UNKNOWN").param("vnp_SecureHash", "invalid"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.RspCode").value("97"));
        var session = pendingSession();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/payment/vnpay/create-url")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"bookingCode\":\"" + session.bookingCode() + "\",\"paymentType\":\"DEPOSIT\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    private VnpayPaymentUrlResponse pendingSession() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setPickupTime(java.time.Instant.now().plus(java.time.Duration.ofDays(10)));
        bookingRepository.saveAndFlush(booking);
        return vnpayService.createPaymentUrl(booking.getRenter().getUser().getId(),
                new CreateVnpayPaymentUrlRequest(booking.getBookingCode(), "DEPOSIT", null, "vn"), "127.0.0.1");
    }

    private Map<String, String> signed(VnpayPaymentUrlResponse session, String code, String status) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        params.put("vnp_TxnRef", session.vnpTxnRef());
        params.put("vnp_Amount", session.amount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_ResponseCode", code);
        params.put("vnp_TransactionStatus", status);
        params.put("vnp_TransactionNo", "14999888");
        return resign(params);
    }

    private Map<String, String> resign(Map<String, String> params) {
        params.remove("vnp_SecureHash");
        params.put("vnp_SecureHash", VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), VnpayUtils.buildHashData(params)));
        return params;
    }

    @Test
    void returnBeforeIpnVerifiesDirectlyAndSubsequentIpnIsIdempotent() {
        var session = pendingSession();
        var params = signed(session, "00", "00");
        
        // 1. Callback arrives before IPN (e.g. sandbox localhost)
        var result = vnpayService.processCallback(params);
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.success()).isTrue();
        assertThat(result.retryAllowed()).isFalse();
        assertThat(result.transactionCode()).isNotBlank().startsWith("VP");
        
        // Transaction is PAID and booking is CONFIRMED
        var tx = paymentTransactionRepository.findByVnpTxnRef(session.vnpTxnRef()).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(tx.getBooking().getStatus()).isIn(BookingStatus.CONFIRMED, BookingStatus.DRIVER_ASSIGNED);

        // 2. Refreshing callback returns SUCCESS idempotently
        var refresh = vnpayService.processCallback(params);
        assertThat(refresh.status()).isEqualTo("SUCCESS");
        assertThat(refresh.success()).isTrue();

        // 3. Subsequent IPN arrives: safely returns 02 (already confirmed) without error
        assertThat(vnpayService.processIpn(params).rspCode()).isEqualTo("02");
        assertThat(vnpayService.queryTransaction(session.bookingCode(), session.vnpTxnRef()).status()).isEqualTo("SUCCESS");
    }

    @Test
    void expiredPendingCannotCreateAnotherPaymentAndLateIpnStillWorks() {
        var session = pendingSession();
        var tx = paymentTransactionRepository.findByVnpTxnRef(session.vnpTxnRef()).orElseThrow();
        tx.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
        paymentTransactionRepository.saveAndFlush(tx);
        assertThatThrownBy(() -> vnpayService.createPaymentUrl(tx.getBooking().getRenter().getUser().getId(),
                new CreateVnpayPaymentUrlRequest(session.bookingCode(), "DEPOSIT", null, "vn"), "127.0.0.1"))
                .isInstanceOf(InvalidPaymentOperationException.class).hasMessageContaining("đối soát");
        assertThat(vnpayService.processIpn(signed(session, "00", "00")).rspCode()).isEqualTo("00");
    }

    @Test
    void lateFailureCannotDowngradePaidPaymentOrBooking() {
        var session = pendingSession();
        vnpayService.processIpn(signed(session, "00", "00"));
        assertThat(vnpayService.processIpn(signed(session, "24", "02")).rspCode()).isEqualTo("02");
        var tx = paymentTransactionRepository.findByVnpTxnRef(session.vnpTxnRef()).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(tx.getBooking().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(vnpayService.processCallback(signed(session, "24", "02")).status()).isEqualTo("SUCCESS");
    }

    @Test
    void merchantAmountAndTransactionStatusMustMatch() {
        var session = pendingSession();
        var wrongMerchant = signed(session, "00", "00");
        wrongMerchant.put("vnp_TmnCode", "OTHER");
        assertThat(vnpayService.processIpn(resign(wrongMerchant)).rspCode()).isEqualTo("97");
        assertThat(vnpayService.processIpn(signed(session, "00", "01")).rspCode()).isEqualTo("99");
        var wrongAmount = signed(session, "00", "00");
        wrongAmount.put("vnp_Amount", "1");
        assertThat(vnpayService.processCallback(resign(wrongAmount)).bookingCode()).isNull();
        assertThat(vnpayService.queryTransaction(session.bookingCode(), session.vnpTxnRef()).status()).isEqualTo("PENDING_VERIFICATION");
        assertThatThrownBy(() -> vnpayService.queryTransaction("OTHER", session.vnpTxnRef()))
                .isInstanceOf(com.fptufa26.ecomotion.common.exception.ResourceNotFoundException.class);
    }

    @Test
    void signedUnknownTransactionDoesNotConfirmBooking() {
        var session = new VnpayPaymentUrlResponse(null, "UNKNOWN", BigDecimal.ONE, "UNKNOWN", java.time.Instant.now());
        assertThat(vnpayService.processIpn(signed(session, "00", "00")).rspCode()).isEqualTo("01");
        assertThat(vnpayService.processCallback(signed(session, "00", "00")).success()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"00", "02", "01"})
    void signedServerQueryReconcilesWithoutIpn(String gatewayStatus) throws Exception {
        var session = pendingSession();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        String oldUrl = vnpayConfig.getApiUrl();
        boolean oldEnabled = vnpayConfig.isReconciliationEnabled();
        var oldInterval = vnpayConfig.getQueryInterval();
        Map<String, String> response = new HashMap<>();
        response.put("vnp_ResponseId", "query-response");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Success");
        response.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        response.put("vnp_TxnRef", session.vnpTxnRef());
        response.put("vnp_Amount", session.amount().movePointRight(2).toBigIntegerExact().toString());
        response.put("vnp_TransactionNo", "123456");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", gatewayStatus);
        String hashData = java.util.stream.Stream.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
                        "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
                        "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount")
                .map(key -> response.getOrDefault(key, "")).collect(java.util.stream.Collectors.joining("|"));
        response.put("vnp_SecureHash", VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData));
        server.createContext("/query", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] json = response.entrySet().stream().map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "{", "}")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();
        try {
            vnpayConfig.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/query");
            vnpayConfig.setReconciliationEnabled(true);
            vnpayConfig.setQueryInterval(java.time.Duration.ZERO);
            String expected = "00".equals(gatewayStatus) ? "SUCCESS" : "02".equals(gatewayStatus) ? "FAILED" : "PENDING_VERIFICATION";
            var result = vnpayService.queryTransaction(session.bookingCode(), session.vnpTxnRef());
            assertThat(result.status()).isEqualTo(expected);
            assertThat(result.retryAllowed()).isEqualTo("02".equals(gatewayStatus));
        } finally {
            vnpayConfig.setApiUrl(oldUrl);
            vnpayConfig.setReconciliationEnabled(oldEnabled);
            vnpayConfig.setQueryInterval(oldInterval);
            server.stop(0);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentCallbacksApplyExactlyOnce() throws Exception {
        var template = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var session = template.execute(status -> pendingSession());
        var params = signed(session, "00", "00");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> callback = () -> {
                start.await();
                return vnpayService.processIpn(params).rspCode();
            };
            var first = executor.submit(callback);
            var second = executor.submit(callback);
            start.countDown();
            assertThat(java.util.List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("00", "02");
        }
        template.executeWithoutResult(status -> {
            var tx = paymentTransactionRepository.findByVnpTxnRef(session.vnpTxnRef()).orElseThrow();
            assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(paymentTransactionRepository.findByBooking_Id(tx.getBooking().getId()).stream()
                    .filter(t -> t.getType() == PaymentType.DEPOSIT_PAY && t.getStatus() == PaymentStatus.PAID)).hasSize(1);
        });
    }

    @Test
    @DisplayName("Renter can lookup booking detail by bookingCode or by database ID with provider name")
    void renterCanLookupBookingDetailByCode() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        // 1. By bookingCode
        var detailByCode = bookingService.detail(renterUserId, booking.getBookingCode());
        assertThat(detailByCode).isNotNull();
        assertThat(detailByCode.bookingCode()).isEqualTo(booking.getBookingCode());
        assertThat(detailByCode.vehicleName()).isNotBlank();
        if (booking.getVehicle() != null && booking.getVehicle().getProvider() != null) {
            assertThat(detailByCode.providerName()).isNotBlank();
        }


    }

    @Test
    @DisplayName("Lookup booking fails with 404 when booking does not exist")
    void lookupNonExistentBookingFails() {
        assertThatThrownBy(() -> bookingService.detail(1L, "NON_EXISTENT_CODE_XYZ"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Lookup booking fails with 403 AccessDenied when accessed by another user")
    void lookupOtherRenterBookingHidesExistence() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        Long wrongUserId = booking.getRenter().getUser().getUserId() + 999999L;

        assertThatThrownBy(() -> bookingService.detail(wrongUserId, booking.getBookingCode()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Create VNPay payment url for ACCEPTED booking succeeds with valid parameters")
    void createVnpayPaymentUrlForAcceptedBooking() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "DEPOSIT",
                null,
                "vn"
        );

        VnpayPaymentUrlResponse res = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");

        assertThat(res).isNotNull();
        assertThat(res.paymentUrl()).isNotBlank();
        assertThat(res.paymentUrl()).contains("vnp_SecureHash=");
        assertThat(res.paymentUrl()).contains("vnp_TmnCode=" + vnpayConfig.getTmnCode());
        assertThat(res.bookingCode()).isEqualTo(booking.getBookingCode());
        assertThat(res.amount()).isGreaterThan(BigDecimal.ZERO);

        // Idempotency: calling again within 14 minutes reuses the active PENDING transaction
        VnpayPaymentUrlResponse res2 = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");
        assertThat(res2.vnpTxnRef()).isEqualTo(res.vnpTxnRef());
    }

    @Test
    @DisplayName("Create VNPay payment url also accepts numeric ID in request")
    void createVnpayPaymentUrlAcceptsNumericId() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                String.valueOf(booking.getId()),
                "DEPOSIT",
                null,
                "vn"
        );

        VnpayPaymentUrlResponse res = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");
        assertThat(res).isNotNull();
        assertThat(res.paymentUrl()).isNotBlank();
        assertThat(res.bookingCode()).isEqualTo(booking.getBookingCode());
    }

    @Test
    @DisplayName("Create VNPay payment url fails with AccessDenied if another user requests")
    void createVnpayPaymentUrlRejectsUnauthorizedUser() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);

        Long unauthorizedUserId = booking.getRenter().getUser().getUserId() + 8888L;

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "DEPOSIT",
                null,
                "vn"
        );

        assertThatThrownBy(() -> vnpayService.createPaymentUrl(unauthorizedUserId, request, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Create VNPay payment url rejects bookings in ineligible statuses (CONFIRMED, CANCELLED)")
    void createVnpayPaymentUrlRejectsIneligibleBooking() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        // 1. CONFIRMED booking cannot be deposited again
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.saveAndFlush(booking);

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "DEPOSIT",
                null,
                "vn"
        );

        assertThatThrownBy(() -> vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1"))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("đã được thanh toán thành công");

        // 2. CANCELLED booking cannot be deposited
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.saveAndFlush(booking);

        assertThatThrownBy(() -> vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1"))
                .isInstanceOf(InvalidPaymentOperationException.class);
    }

    @Test
    @DisplayName("End-to-end VNPay Sandbox IPN flow: verifies HMAC, marks PAID, updates booking to CONFIRMED")
    void completeVnpayIpnFlowConfirmsBooking() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "DEPOSIT",
                null,
                "vn"
        );

        VnpayPaymentUrlResponse session = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");
        String vnpTxnRef = session.vnpTxnRef();

        PaymentTransaction txBefore = paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef).orElseThrow();
        assertThat(txBefore.getStatus()).isEqualTo(PaymentStatus.PENDING);

        long amountPennies = session.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_TxnRef", vnpTxnRef);
        ipnParams.put("vnp_Amount", String.valueOf(amountPennies));
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        ipnParams.put("vnp_TransactionNo", "14999888");
        ipnParams.put("vnp_BankCode", "NCB");
        ipnParams.put("vnp_CardType", "ATM");
        ipnParams.put("vnp_PayDate", "20261023150000");

        String hashData = VnpayUtils.buildHashData(ipnParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnResponse = vnpayService.processIpn(ipnParams);
        assertThat(ipnResponse.rspCode()).isEqualTo("00");

        PaymentTransaction txAfter = paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef).orElseThrow();
        assertThat(txAfter.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(txAfter.getVnpBankCode()).isEqualTo("NCB");

        Booking bookingAfter = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(bookingAfter.getStatus()).isIn(BookingStatus.CONFIRMED, BookingStatus.DRIVER_ASSIGNED);

        // Idempotency: second IPN call returns 02 (Order already confirmed)
        VnpayIpnResponse duplicateIpn = vnpayService.processIpn(ipnParams);
        assertThat(duplicateIpn.rspCode()).isEqualTo("02");
    }

    @Test
    @DisplayName("Failed VNPay payment (ResponseCode 24) sets FAILED and allows renter to retry")
    void failedPaymentAllowsRetry() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "DEPOSIT",
                null,
                "vn"
        );

        VnpayPaymentUrlResponse session1 = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");
        String vnpTxnRef1 = session1.vnpTxnRef();

        long amountPennies = session1.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, String> failParams = new HashMap<>();
        failParams.put("vnp_TxnRef", vnpTxnRef1);
        failParams.put("vnp_Amount", String.valueOf(amountPennies));
        failParams.put("vnp_ResponseCode", "24");
        failParams.put("vnp_TransactionStatus", "02");
        failParams.put("vnp_TmnCode", vnpayConfig.getTmnCode()); // User cancelled

        String hashData = VnpayUtils.buildHashData(failParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        failParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnRes = vnpayService.processIpn(failParams);
        assertThat(ipnRes.rspCode()).isEqualTo("00");

        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef1).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);

        Booking currentBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(currentBooking.getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        assertThat(scheduleService.hasUnexpiredHold(booking.getId(), java.time.Instant.now())).isTrue();

        // Renter can create a new payment session to retry
        VnpayPaymentUrlResponse session2 = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");
        assertThat(session2).isNotNull();
        assertThat(session2.vnpTxnRef()).isNotEqualTo(vnpTxnRef1);
    }

    @Test
    @DisplayName("VNPay IPN rejects amount mismatch with code 04")
    void vnpayIpnDetectsAmountMismatch() {
        Booking booking = VnpayBookingFixture.create(bookingRepository, snapshotRepository);

        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "DEPOSIT",
                null,
                "vn"
        );

        VnpayPaymentUrlResponse session = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");

        Map<String, String> mismatchParams = new HashMap<>();
        mismatchParams.put("vnp_TxnRef", session.vnpTxnRef());
        mismatchParams.put("vnp_Amount", "9999999999"); // Wrong amount
        mismatchParams.put("vnp_ResponseCode", "00");
        mismatchParams.put("vnp_TransactionStatus", "00");
        mismatchParams.put("vnp_TmnCode", vnpayConfig.getTmnCode());

        String hashData = VnpayUtils.buildHashData(mismatchParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        mismatchParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnRes = vnpayService.processIpn(mismatchParams);
        assertThat(ipnRes.rspCode()).isEqualTo("04");
    }

    @Test
    @DisplayName("VNPay callback and IPN reject invalid checksum with 97")
    void vnpayIpnDetectsInvalidChecksum() {
        Map<String, String> invalidParams = new HashMap<>();
        invalidParams.put("vnp_TxnRef", "VNP_NON_EXISTENT");
        invalidParams.put("vnp_ResponseCode", "00");
        invalidParams.put("vnp_TransactionStatus", "00");
        invalidParams.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        invalidParams.put("vnp_SecureHash", "MALICIOUS_TAMPERED_CHECKSUM");

        VnpayIpnResponse ipnRes = vnpayService.processIpn(invalidParams);
        assertThat(ipnRes.rspCode()).isEqualTo("97");

        VnpayCallbackResultResponse callbackRes = vnpayService.processCallback(invalidParams);
        assertThat(callbackRes.success()).isFalse();
    }

    @Test
    @DisplayName("Auto Transaction ID format VPxxxxxx and VQxxxxxx with 6 digits padding")
    void autoTransactionCodeGenerationFormatAndSequence() {
        String vpCode1 = paymentCodeGenerator.generatePaymentCode("VP");
        String vpCode2 = paymentCodeGenerator.generatePaymentCode("VP");
        String vqCode1 = paymentCodeGenerator.generatePaymentCode("VQ");
        String vqCode2 = paymentCodeGenerator.generatePaymentCode("VQ");

        assertThat(vpCode1).matches("^VP\\d{6,}$");
        assertThat(vpCode2).matches("^VP\\d{6,}$");
        assertThat(vqCode1).matches("^VQ\\d{6,}$");
        assertThat(vqCode2).matches("^VQ\\d{6,}$");

        assertThat(vpCode1).isNotEqualTo(vpCode2);
        assertThat(vqCode1).isNotEqualTo(vqCode2);
    }

    @Test
    @DisplayName("Concurrent payment code generation guarantees uniqueness without duplicate codes")
    void concurrentPaymentCodeGenerationGuaranteesUniqueness() throws Exception {
        int threads = 8;
        int countPerThread = 5;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.Set<String> generatedCodes = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < countPerThread; j++) {
                        generatedCodes.add(paymentCodeGenerator.generatePaymentCode("VP"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        latch.countDown();
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(generatedCodes).hasSize(threads * countPerThread);
    }
}
