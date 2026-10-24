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

    @Test
    @DisplayName("Renter can lookup booking detail by bookingCode or by database ID with provider name")
    void renterCanLookupBookingDetailByCodeOrId() {
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

        Long renterUserId = booking.getRenter().getUser().getUserId();

        // 1. By bookingCode
        var detailByCode = bookingService.detail(renterUserId, booking.getBookingCode());
        assertThat(detailByCode).isNotNull();
        assertThat(detailByCode.bookingCode()).isEqualTo(booking.getBookingCode());
        assertThat(detailByCode.vehicleName()).isNotBlank();
        if (booking.getVehicle() != null && booking.getVehicle().getProvider() != null) {
            assertThat(detailByCode.providerName()).isNotBlank();
        }

        // 2. By numeric database ID string
        var detailById = bookingService.detail(renterUserId, String.valueOf(booking.getId()));
        assertThat(detailById).isNotNull();
        assertThat(detailById.bookingCode()).isEqualTo(booking.getBookingCode());
    }

    @Test
    @DisplayName("Lookup booking fails with 404 when booking does not exist")
    void lookupNonExistentBookingFails() {
        assertThatThrownBy(() -> bookingService.detail(1L, "NON_EXISTENT_CODE_XYZ"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Lookup booking fails with 403 AccessDenied when accessed by another user")
    void lookupOtherRenterBookingFailsAccessDenied() {
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

        Long wrongUserId = booking.getRenter().getUser().getUserId() + 999999L;

        assertThatThrownBy(() -> bookingService.detail(wrongUserId, booking.getBookingCode()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Create VNPay payment url for ACCEPTED booking succeeds with valid parameters")
    void createVnpayPaymentUrlForAcceptedBooking() {
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

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
    }

    @Test
    @DisplayName("Create VNPay payment url also accepts numeric ID in request")
    void createVnpayPaymentUrlAcceptsNumericId() {
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

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
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

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
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

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
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

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
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

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
        failParams.put("vnp_ResponseCode", "24"); // User cancelled

        String hashData = VnpayUtils.buildHashData(failParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        failParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnRes = vnpayService.processIpn(failParams);
        assertThat(ipnRes.rspCode()).isEqualTo("00");

        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef1).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // Booking status remains ACCEPTED
        Booking currentBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(currentBooking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);

        // Renter can create a new payment session to retry
        VnpayPaymentUrlResponse session2 = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");
        assertThat(session2).isNotNull();
        assertThat(session2.vnpTxnRef()).isNotEqualTo(vnpTxnRef1);
    }

    @Test
    @DisplayName("VNPay IPN rejects amount mismatch with code 04")
    void vnpayIpnDetectsAmountMismatch() {
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

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
        invalidParams.put("vnp_SecureHash", "MALICIOUS_TAMPERED_CHECKSUM");

        VnpayIpnResponse ipnRes = vnpayService.processIpn(invalidParams);
        assertThat(ipnRes.rspCode()).isEqualTo("97");

        VnpayCallbackResultResponse callbackRes = vnpayService.processCallback(invalidParams);
        assertThat(callbackRes.success()).isFalse();
    }
}
