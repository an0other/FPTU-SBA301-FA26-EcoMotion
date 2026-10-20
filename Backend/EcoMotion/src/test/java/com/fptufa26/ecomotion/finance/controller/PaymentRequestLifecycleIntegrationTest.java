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
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class PaymentRequestLifecycleIntegrationTest {

    @Autowired PaymentRequestService paymentRequestService;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingChargeRepository chargeRepository;
    @Autowired VehicleScheduleRepository scheduleRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired ProviderBankAccountRepository bankAccountRepository;
    @Autowired PaymentTransactionRepository transactionRepository;
    @Autowired ProviderBookingService providerBookingService;
    @Autowired BookingService bookingService;
    @Autowired DisputeService disputeService;
    @Autowired DisputeRepository disputeRepository;
    @Autowired PaymentRequestRepository paymentRequestRepository;

    @Test
    void confirmingTheFullDepositLocksTheScheduleAndCreatesTheDepositLedgerEntry() {
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setProviderConfirmedAt(null);
        bookingRepository.saveAndFlush(booking);

        if (bankAccountRepository.findByProvider_IdAndStatus(
                booking.getVehicle().getProvider().getId(),
                com.fptufa26.ecomotion.finance.enums.BankAccountStatus.ACTIVE).isEmpty()) {
            ProviderBankAccount account = new ProviderBankAccount();
            account.setProvider(providerRepository.findById(booking.getVehicle().getProvider().getId()).orElseThrow());
            account.setBankCode("VCB");
            account.setBankName("Vietcombank");
            account.setAccountNumber("0123456789");
            account.setAccountHolderName("ECOMOTION TEST");
            bankAccountRepository.saveAndFlush(account);
        }

        PaymentRequest request = paymentRequestService.createDepositPaymentRequest(booking);
        assertThatThrownBy(() -> paymentRequestService.confirmPayment(request.getId(), 2L,
                new ConfirmPaymentRequest(request.getAmount(), "NO-PROOF", null)))
                .hasMessageContaining("chứng từ");
        request.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
        paymentRequestRepository.saveAndFlush(request);
        paymentRequestService.confirmPayment(request.getId(), 2L,
                new ConfirmPaymentRequest(request.getAmount(), "TDD-DEPOSIT-LOCK-001", null));

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(scheduleRepository.findByBooking_Id(booking.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(ScheduleStatus.ACTIVE));
        assertThat(chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.DEPOSIT))
                .isPresent()
                .get()
                .extracting(charge -> charge.getAmount().setScale(2))
                .isEqualTo(request.getAmount().setScale(2));
    }

    @Test
    void finalizingSettlementUsesBaseRentalOnceAndIsIdempotent() {
        Booking booking = bookingRepository.findByBookingCode("BK-4A2K91").orElseThrow();
        booking.setTotalAmount(new BigDecimal("1000000.00"));
        booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        booking.setActualEndAt(Instant.parse("2026-10-19T10:00:00Z"));
        booking.setProviderConfirmedAt(Instant.parse("2026-10-19T10:00:00Z"));
        booking.setRenterConfirmedAt(Instant.parse("2026-10-19T10:05:00Z"));
        bookingRepository.saveAndFlush(booking);

        BookingCharge base = new BookingCharge();
        base.setBooking(booking);
        base.setChargeType(ChargeType.BASE_RENTAL);
        base.setDescription("Base rental ledger entry");
        base.setQuantity(1);
        base.setUnitPrice(new BigDecimal("1000000.00"));
        base.setAmount(new BigDecimal("1000000.00"));
        base.setCreatedBy(booking.getRenter().getUser());
        base.setWaived(false);
        chargeRepository.save(base);

        BookingCharge extra = new BookingCharge();
        extra.setBooking(booking);
        extra.setChargeType(ChargeType.WAITING);
        extra.setDescription("Waiting fee");
        extra.setAmount(new BigDecimal("100000.00"));
        extra.setUnitPrice(new BigDecimal("100000.00"));
        extra.setCreatedBy(booking.getVehicle().getProvider().getUser());
        extra.setWaived(false);
        chargeRepository.save(extra);

        PaymentTransaction deposit = new PaymentTransaction();
        deposit.setBooking(booking);
        deposit.setType(PaymentType.DEPOSIT_PAY);
        deposit.setDirection(PaymentDirection.IN);
        deposit.setMethod(PaymentMethod.BANK_TRANSFER);
        deposit.setStatus(PaymentStatus.PAID);
        deposit.setAmount(new BigDecimal("300000.00"));
        deposit.setPaidAt(Instant.parse("2026-10-18T10:00:00Z"));
        transactionRepository.save(deposit);

        ensureBankAccount(booking);

        var first = providerBookingService.finalizeSettlement(2L, booking.getBookingCode());
        var second = providerBookingService.finalizeSettlement(2L, booking.getBookingCode());

        assertThat(first.status()).isEqualTo(BookingStatus.AWAITING_FINAL_PAYMENT);
        assertThat(first.remainingAmount()).isEqualByComparingTo("800000.00");
        assertThat(second.paymentRequestId()).isEqualTo(first.paymentRequestId());

        PaymentRequest existing = paymentRequestRepository.findById(first.paymentRequestId()).orElseThrow();
        existing.setStatus(PaymentRequestStatus.REJECTED);
        paymentRequestRepository.saveAndFlush(existing);
        var retried = providerBookingService.finalizeSettlement(2L, booking.getBookingCode());
        assertThat(retried.paymentRequestId()).isEqualTo(first.paymentRequestId());
    }

    @Test
    void chargeDisputeHoldsFinalPaymentAndAdminWaiverRecalculatesIt() {
        Booking booking = bookingRepository.findByBookingCode("BK-4A2K91").orElseThrow();
        booking.setTotalAmount(new BigDecimal("1000000.00"));
        booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        booking.setActualEndAt(Instant.parse("2026-10-19T10:00:00Z"));
        booking.setProviderConfirmedAt(Instant.parse("2026-10-19T10:00:00Z"));
        booking.setRenterConfirmedAt(Instant.parse("2026-10-19T10:05:00Z"));
        bookingRepository.saveAndFlush(booking);

        BookingCharge extra = new BookingCharge();
        extra.setBooking(booking);
        extra.setChargeType(ChargeType.WAITING);
        extra.setDescription("Waiting fee disputed by renter");
        extra.setAmount(new BigDecimal("100000.00"));
        extra.setUnitPrice(new BigDecimal("100000.00"));
        extra.setCreatedBy(booking.getVehicle().getProvider().getUser());
        extra.setWaived(false);
        chargeRepository.saveAndFlush(extra);

        PaymentTransaction deposit = new PaymentTransaction();
        deposit.setBooking(booking);
        deposit.setType(PaymentType.DEPOSIT_PAY);
        deposit.setDirection(PaymentDirection.IN);
        deposit.setMethod(PaymentMethod.BANK_TRANSFER);
        deposit.setStatus(PaymentStatus.PAID);
        deposit.setAmount(new BigDecimal("300000.00"));
        deposit.setPaidAt(Instant.parse("2026-10-18T10:00:00Z"));
        transactionRepository.save(deposit);
        ensureBankAccount(booking);

        providerBookingService.finalizeSettlement(2L, booking.getBookingCode());
        bookingService.openDispute(
                booking.getRenter().getUser().getId(),
                booking.getBookingCode(),
                new OpenDisputeRequest(DisputeCategory.CHARGE, ChargeType.WAITING));

        PaymentRequest held = paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                .orElseThrow();
        assertThat(held.getStatus()).isEqualTo(PaymentRequestStatus.REVIEW_REQUIRED);

        var dispute = disputeRepository.findByBooking_Id(booking.getId()).stream()
                .filter(row -> row.getCharge() != null && row.getCharge().getId().equals(extra.getId()))
                .findFirst().orElseThrow();
        disputeService.resolveDispute(
                dispute.getId(), 3L, DisputeStatus.RESOLVED,
                DisputeResolutionDecision.WAIVE_CHARGE, "Phụ phí không đủ bằng chứng");

        assertThat(chargeRepository.findById(extra.getId()).orElseThrow().isWaived()).isTrue();
        PaymentRequest reopened = paymentRequestRepository.findById(held.getId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(reopened.getAmount()).isEqualByComparingTo("700000.00");
    }

    private void ensureBankAccount(Booking booking) {
        if (bankAccountRepository.findByProvider_IdAndStatus(
                booking.getVehicle().getProvider().getId(),
                com.fptufa26.ecomotion.finance.enums.BankAccountStatus.ACTIVE).isPresent()) {
            return;
        }
        ProviderBankAccount account = new ProviderBankAccount();
        account.setProvider(providerRepository.findById(booking.getVehicle().getProvider().getId()).orElseThrow());
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumber("0123456789");
        account.setAccountHolderName("ECOMOTION TEST");
        bankAccountRepository.saveAndFlush(account);
    }
}
