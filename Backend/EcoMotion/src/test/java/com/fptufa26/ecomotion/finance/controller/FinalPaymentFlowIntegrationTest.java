package com.fptufa26.ecomotion.finance.controller;

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
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.config.VnpayUtils;
import com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest;
import com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayIpnResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class FinalPaymentFlowIntegrationTest {

    @Autowired
    private VnpayService vnpayService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingChargeRepository bookingChargeRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private VnpayConfig vnpayConfig;

    private Booking createSampleBookingForFinalPayment(BigDecimal totalAmount, BigDecimal depositAmount) {
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> b.getRenter() != null && b.getRenter().getUser() != null)
                .findFirst()
                .orElseThrow();

        booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
        booking.setTotalAmount(totalAmount);
        booking.setActualStartAt(Instant.now().minus(4, ChronoUnit.HOURS));
        booking.setActualEndAt(Instant.now().minus(1, ChronoUnit.HOURS));
        final Booking savedBooking = bookingRepository.saveAndFlush(booking);

        // Record successful deposit
        PaymentTransaction depositTx = new PaymentTransaction();
        depositTx.setBooking(savedBooking);
        depositTx.setAmount(depositAmount);
        depositTx.setType(PaymentType.DEPOSIT_PAY);
        depositTx.setDirection(PaymentDirection.IN);
        depositTx.setMethod(PaymentMethod.VNPAY);
        depositTx.setStatus(PaymentStatus.PAID);
        depositTx.setVnpTxnRef("DEP-TEST-" + System.currentTimeMillis() + "-" + savedBooking.getId());
        depositTx.setPaidAt(Instant.now().minus(5, ChronoUnit.HOURS));
        paymentTransactionRepository.saveAndFlush(depositTx);

        // Sync with PaymentRequest for final balance
        PaymentRequest pr = paymentRequestRepository.findByBooking_IdAndPaymentType(savedBooking.getId(), PaymentRequestType.FINAL_BALANCE)
                .orElseGet(() -> {
                    PaymentRequest req = new PaymentRequest();
                    req.setBooking(savedBooking);
                    req.setPaymentType(PaymentRequestType.FINAL_BALANCE);
                    req.setCurrency("VND");
                    req.setTransferReference("REF-FINAL-" + System.currentTimeMillis() + "-" + savedBooking.getId());
                    req.setBankCodeSnapshot("NCB");
                    req.setBankNameSnapshot("Ngan hang Quoc Dan");
                    req.setAccountNumberSnapshot("9704198526191432198");
                    req.setAccountHolderSnapshot("NGUYEN VAN A");
                    req.setExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
                    return req;
                });
        pr.setAmount(totalAmount.subtract(depositAmount));
        pr.setStatus(PaymentRequestStatus.PENDING);
        paymentRequestRepository.saveAndFlush(pr);

        return savedBooking;
    }

    @Test
    @DisplayName("TC01: DEPOSIT thành công, FINAL_PAYMENT vẫn tạo được bình thường")
    void tc01_depositSuccessDoesNotBlockFinalPayment() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "FINAL_PAYMENT",
                null,
                "vn"
        );

        VnpayPaymentUrlResponse response = vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.paymentUrl()).isNotBlank();
        assertThat(response.paymentUrl()).contains("sandbox.vnpayment.vn");
        assertThat(response.vnpTxnRef()).contains(booking.getBookingCode());
        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(response.vnpTxnRef()).orElseThrow();
        assertThat(tx.getType()).isEqualTo(PaymentType.SETTLEMENT);
    }

    @Test
    @DisplayName("TC02: Trip chưa kết thúc thì không cho phép tạo FINAL_PAYMENT")
    void tc02_tripNotEndedRejectsFinalPayment() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setActualEndAt(null);
        bookingRepository.saveAndFlush(booking);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        CreateVnpayPaymentUrlRequest request = new CreateVnpayPaymentUrlRequest(
                booking.getBookingCode(),
                "FINAL_PAYMENT",
                null,
                "vn"
        );

        assertThatThrownBy(() -> vnpayService.createPaymentUrl(renterUserId, request, "127.0.0.1"))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("Chuyến đi chưa kết thúc");
    }

    @Test
    @DisplayName("TC03: Trip kết thúc, quyết toán hợp lệ, tạo VNPay Sandbox URL thành công")
    void tc03_tripEndedValidSettlementCreatesVnpayUrl() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        VnpayPaymentUrlResponse res = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");

        assertThat(res).isNotNull();
        assertThat(res.paymentUrl()).contains("vnp_Amount=98000000");
        assertThat(res.vnpTxnRef()).contains(booking.getBookingCode());

        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(res.vnpTxnRef()).orElseThrow();
        assertThat(tx.getType()).isEqualTo(PaymentType.SETTLEMENT);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("TC04: Thanh toán FINAL_PAYMENT thành công, Booking cập nhật COMPLETED và Transaction PAID")
    void tc04_successfulFinalPaymentCompletesBooking() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        VnpayPaymentUrlResponse res = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");

        long amountPennies = res.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_TxnRef", res.vnpTxnRef());
        ipnParams.put("vnp_Amount", String.valueOf(amountPennies));
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TransactionNo", "14567890");
        ipnParams.put("vnp_BankCode", "NCB");
        ipnParams.put("vnp_CardType", "ATM");
        ipnParams.put("vnp_PayDate", "20261024103000");

        String hashData = VnpayUtils.buildHashData(ipnParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnRes = vnpayService.processIpn(ipnParams);
        assertThat(ipnRes.rspCode()).isEqualTo("00");

        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(res.vnpTxnRef()).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(tx.getPaidAt()).isNotNull();

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("TC05: Thanh toán bị hủy, transaction FAILED, có thể thử lại")
    void tc05_cancelledPaymentSetsFailedAndAllowsRetry() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        VnpayPaymentUrlResponse session1 = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");
        long amountPennies = session1.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, String> failParams = new HashMap<>();
        failParams.put("vnp_TxnRef", session1.vnpTxnRef());
        failParams.put("vnp_Amount", String.valueOf(amountPennies));
        failParams.put("vnp_ResponseCode", "24"); // Cancelled by customer

        String hashData = VnpayUtils.buildHashData(failParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        failParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse ipnRes = vnpayService.processIpn(failParams);
        assertThat(ipnRes.rspCode()).isEqualTo("00");

        PaymentTransaction tx = paymentTransactionRepository.findByVnpTxnRef(session1.vnpTxnRef()).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);

        Booking bookingStillWaiting = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(bookingStillWaiting.getStatus()).isEqualTo(BookingStatus.AWAITING_FINAL_PAYMENT);

        // Can retry payment
        VnpayPaymentUrlResponse session2 = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");
        assertThat(session2).isNotNull();
        assertThat(session2.vnpTxnRef()).isNotEqualTo(session1.vnpTxnRef());
    }

    @Test
    @DisplayName("TC06: FINAL_PAYMENT thành công, gọi lại tạo thanh toán bị chặn đúng cách")
    void tc06_alreadySettledBlocksDuplicatePayment() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.saveAndFlush(booking);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        assertThatThrownBy(() -> vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1"))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("thanh toán hoàn tất");
    }

    @Test
    @DisplayName("TC07: Duplicate IPN không tạo thanh toán trùng (trả về 02 Order already confirmed)")
    void tc07_duplicateIpnReturns02AlreadyConfirmed() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        VnpayPaymentUrlResponse res = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");

        long amountPennies = res.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_TxnRef", res.vnpTxnRef());
        ipnParams.put("vnp_Amount", String.valueOf(amountPennies));
        ipnParams.put("vnp_ResponseCode", "00");

        String hashData = VnpayUtils.buildHashData(ipnParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        ipnParams.put("vnp_SecureHash", secureHash);

        VnpayIpnResponse firstIpn = vnpayService.processIpn(ipnParams);
        assertThat(firstIpn.rspCode()).isEqualTo("00");

        // Duplicate IPN
        VnpayIpnResponse secondIpn = vnpayService.processIpn(ipnParams);
        assertThat(secondIpn.rspCode()).isEqualTo("02");
    }

    @Test
    @DisplayName("TC08: Có phụ phí hợp lệ thì số dư tăng đúng theo công thức")
    void tc08_extraChargesIncreasePayableAmount() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));

        // Add 150.000 overtime charge and 50.000 waiting charge
        BookingCharge overtime = new BookingCharge();
        overtime.setBooking(booking);
        overtime.setChargeType(ChargeType.OVERTIME);
        overtime.setDescription("Phụ phí quá giờ 1 tiếng");
        overtime.setAmount(new BigDecimal("150000.00"));
        overtime.setUnitPrice(new BigDecimal("150000.00"));
        overtime.setQuantity(1);
        overtime.setWaived(false);
        overtime.setCreatedBy(booking.getRenter().getUser());
        bookingChargeRepository.saveAndFlush(overtime);

        BookingCharge waiting = new BookingCharge();
        waiting.setBooking(booking);
        waiting.setChargeType(ChargeType.WAITING);
        waiting.setDescription("Phụ phí chờ đón khách 30 phút");
        waiting.setAmount(new BigDecimal("50000.00"));
        waiting.setUnitPrice(new BigDecimal("50000.00"));
        waiting.setQuantity(1);
        waiting.setWaived(false);
        waiting.setCreatedBy(booking.getRenter().getUser());
        bookingChargeRepository.saveAndFlush(waiting);

        Long renterUserId = booking.getRenter().getUser().getUserId();
        RenterSettlementResponse sett = vnpayService.getSettlementDetails(renterUserId, booking.getBookingCode());

        // totalObligation = 1.400.000 + 200.000 = 1.600.000
        // netPaid = 420.000 (deposit)
        // finalPayable = 1.600.000 - 420.000 = 1.180.000
        assertThat(sett.totalCharges()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(sett.finalPayable()).isEqualByComparingTo(new BigDecimal("1180000.00"));

        VnpayPaymentUrlResponse urlRes = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");
        assertThat(urlRes.amount()).isEqualByComparingTo(new BigDecimal("1180000.00"));
    }

    @Test
    @DisplayName("TC09: finalPayable bằng 0, không gọi VNPay, hoàn tất đơn trực tiếp")
    void tc09_zeroPayableCompletesWithoutVnpayCall() {
        // Base rental 500.000, Deposit already paid 500.000 -> finalPayable = 0
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("500000.00"), new BigDecimal("500000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        RenterSettlementResponse sett = vnpayService.getSettlementDetails(renterUserId, booking.getBookingCode());
        assertThat(sett.finalPayable()).isEqualByComparingTo(BigDecimal.ZERO);

        VnpayPaymentUrlResponse res = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");
        assertThat(res.paymentUrl()).isBlank();
        assertThat(res.amount()).isEqualByComparingTo(BigDecimal.ZERO);

        Booking completedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(completedBooking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("TC10: Refund/overpayment không bị tính thành số dư phải thu âm")
    void tc10_overpaymentDoesNotResultInNegativePayable() {
        // Base rental 400.000, Deposit paid 500.000 -> overpayment 100.000, finalPayable = 0
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("400000.00"), new BigDecimal("500000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        RenterSettlementResponse sett = vnpayService.getSettlementDetails(renterUserId, booking.getBookingCode());
        assertThat(sett.finalPayable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(sett.overpayment()).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    @DisplayName("TC11: Tra cứu và thanh toán bằng bookingCode hoặc ID database đều chính xác")
    void tc11_bookingCodeAndDatabaseIdBothWork() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        RenterSettlementResponse byCode = vnpayService.getSettlementDetails(renterUserId, booking.getBookingCode());
        RenterSettlementResponse byId = vnpayService.getSettlementDetails(renterUserId, String.valueOf(booking.getId()));

        assertThat(byCode.bookingCode()).isEqualTo(byId.bookingCode());
        assertThat(byCode.finalPayable()).isEqualByComparingTo(byId.finalPayable());
    }

    @Test
    @DisplayName("TC12: Refresh trang không mất trạng thái quyết toán (idempotent settlement view)")
    void tc12_refreshDoesNotLoseSettlementState() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        RenterSettlementResponse firstCall = vnpayService.getSettlementDetails(renterUserId, booking.getBookingCode());
        RenterSettlementResponse secondCall = vnpayService.getSettlementDetails(renterUserId, booking.getBookingCode());

        assertThat(firstCall.finalPayable()).isEqualByComparingTo(secondCall.finalPayable());
        assertThat(firstCall.isSettled()).isEqualTo(secondCall.isSettled());
        assertThat(firstCall.eligibleForPayment()).isEqualTo(secondCall.eligibleForPayment());
    }

    @Test
    @DisplayName("TC13: Renter khác không thể thanh toán thay nếu không được ủy quyền (AccessDenied 403)")
    void tc13_otherRenterCannotPayUnauthorized() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long unauthorizedUserId = booking.getRenter().getUser().getUserId() + 99999L;

        assertThatThrownBy(() -> vnpayService.createFinalPaymentUrl(unauthorizedUserId, booking.getBookingCode(), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("TC14: Nếu có dispute OPEN/IN_REVIEW, trả lỗi nghiệp vụ rõ ràng")
    void tc14_openDisputeBlocksFinalPaymentWithClearError() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));

        Dispute dispute = new Dispute();
        dispute.setBooking(booking);
        dispute.setRaisedBy(booking.getRenter().getUser());
        dispute.setCategory(DisputeCategory.CHARGE);
        dispute.setStatus(DisputeStatus.OPEN);
        disputeRepository.saveAndFlush(dispute);

        Long renterUserId = booking.getRenter().getUser().getUserId();

        assertThatThrownBy(() -> vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1"))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("tranh chấp khiếu nại");
    }

    @Test
    @DisplayName("TC15: Dữ liệu tài chính nhất quán giữa Renter, Provider và Admin (PaymentRequest đồng bộ CONFIRMED)")
    void tc15_financialConsistencyAcrossRoles() {
        Booking booking = createSampleBookingForFinalPayment(new BigDecimal("1400000.00"), new BigDecimal("420000.00"));
        Long renterUserId = booking.getRenter().getUser().getUserId();

        VnpayPaymentUrlResponse res = vnpayService.createFinalPaymentUrl(renterUserId, booking.getBookingCode(), "127.0.0.1");

        long amountPennies = res.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        Map<String, String> ipnParams = new HashMap<>();
        ipnParams.put("vnp_TxnRef", res.vnpTxnRef());
        ipnParams.put("vnp_Amount", String.valueOf(amountPennies));
        ipnParams.put("vnp_ResponseCode", "00");

        String hashData = VnpayUtils.buildHashData(ipnParams);
        String secureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        ipnParams.put("vnp_SecureHash", secureHash);

        vnpayService.processIpn(ipnParams);

        // Verify PaymentRequest was confirmed
        PaymentRequest pr = paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE).orElseThrow();
        assertThat(pr.getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(pr.getPaidAmount()).isEqualByComparingTo(new BigDecimal("980000.00"));
        assertThat(pr.getConfirmedAt()).isNotNull();
    }
}
