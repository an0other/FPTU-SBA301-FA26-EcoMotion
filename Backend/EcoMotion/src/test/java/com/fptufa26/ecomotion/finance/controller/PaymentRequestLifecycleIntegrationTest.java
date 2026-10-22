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
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.PaymentEvidenceRepository;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    @Autowired PaymentEvidenceRepository paymentEvidenceRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mockMvc;

    @Test
    void renterUploadsImageProofAndInvalidFilesDoNotCreateEvidence() throws Exception {
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);
        ensureBankAccount(booking);
        PaymentRequest request = paymentRequestService.createDepositPaymentRequest(booking);
        Long renterUserId = booking.getRenter().getUser().getId();

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile empty = new MockMultipartFile("file", "receipt.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> paymentRequestService.submitPaymentProof(
                request.getId(), renterUserId, empty, request.getAmount(), null, null, null))
                .hasMessageContaining("trống");
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "receipt.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> paymentRequestService.submitPaymentProof(
                request.getId(), renterUserId, oversized, request.getAmount(), null, null, null))
                .hasMessageContaining("5 MB");
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "receipt.png", "image/png", new byte[] {1, 2, 3, 4});
        assertThatThrownBy(() -> paymentRequestService.submitPaymentProof(
                request.getId(), renterUserId, spoofed, request.getAmount(), null, null, null))
                .hasMessageContaining("không khớp");
        assertThatThrownBy(() -> paymentRequestService.submitPaymentProof(
                request.getId(), -999L, new MockMultipartFile("file", "receipt.png", "image/png", png),
                request.getAmount(), null, null, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        MockMultipartFile image = new MockMultipartFile("file", "receipt.png", "image/png", png);
        var submitted = paymentRequestService.submitPaymentProof(
                request.getId(), renterUserId, image, request.getAmount(),
                "BANK-IMAGE-001", null, "VietQR receipt");
        assertThat(submitted.proofImageUrl()).startsWith("/api/v1/payment-evidences/");
        assertThat(paymentRequestService.getPaymentEvidenceFile(submitted.id(), renterUserId).content())
                .containsExactly(png);
        assertThatThrownBy(() -> paymentRequestService.getPaymentEvidenceFile(submitted.id(), -999L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(paymentRequestRepository.findById(request.getId()).orElseThrow().getEvidences()).hasSize(1);

        String renterToken = jwtService.generateAccessToken(booking.getRenter().getUser());
        String providerToken = jwtService.generateAccessToken(booking.getVehicle().getProvider().getUser());
        String adminToken = jwtService.generateAccessToken(userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN).findFirst().orElseThrow());
        String storedPath = paymentEvidenceRepository.findById(submitted.id()).orElseThrow().getProofImageUrl();

        mockMvc.perform(get("/api/v1/payment-evidences/{id}/file", submitted.id()))
                .andExpect(status().isUnauthorized());
        for (String token : java.util.List.of(renterToken, providerToken, adminToken)) {
            mockMvc.perform(get("/api/v1/payment-evidences/{id}/file", submitted.id())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(content().contentType(MediaType.IMAGE_PNG))
                    .andExpect(content().bytes(png));
        }
        mockMvc.perform(get("/api/files/" + storedPath))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/payment-requests/{id}/proofs", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isGone());

        PaymentRequest retry = paymentRequestRepository.findById(request.getId()).orElseThrow();
        retry.setStatus(PaymentRequestStatus.REJECTED);
        retry.getEvidences().getFirst().setStatus(
                com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus.REJECTED);
        paymentRequestRepository.saveAndFlush(retry);
        var second = paymentRequestService.submitPaymentProof(
                request.getId(), renterUserId,
                new MockMultipartFile("file", "receipt.png", "image/png", png),
                request.getAmount(), "BANK-IMAGE-002", null, null);
        assertThat(second.proofImageUrl()).isNotEqualTo(submitted.proofImageUrl());

        retry = paymentRequestRepository.findById(request.getId()).orElseThrow();
        retry.setStatus(PaymentRequestStatus.REJECTED);
        retry.getEvidences().getFirst().setStatus(
                com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus.REJECTED);
        paymentRequestRepository.saveAndFlush(retry);
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "receipt.pdf", "application/pdf", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> paymentRequestService.submitPaymentProof(
                request.getId(), renterUserId, pdf, request.getAmount(), null, null, null))
                .hasMessageContaining("JPG");
        assertThat(paymentRequestRepository.findById(request.getId()).orElseThrow().getEvidences()).hasSize(2);
    }

    @Test
    void expiredRequestRejectsProofAndIsNotAdvertisedAsPayable() {
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);
        ensureBankAccount(booking);
        PaymentRequest request = paymentRequestService.createDepositPaymentRequest(booking);
        request.setExpiresAt(Instant.now().minusSeconds(1));
        paymentRequestRepository.saveAndFlush(request);

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertThatThrownBy(() -> paymentRequestService.submitPaymentProof(
                request.getId(), booking.getRenter().getUser().getId(),
                new MockMultipartFile("file", "receipt.png", "image/png", png),
                request.getAmount(), null, null, null))
                .hasMessageContaining("hết hạn");
        assertThat(bookingService.listMine(booking.getRenter().getUser().getId(), null)).filteredOn(
                summary -> summary.bookingCode().equals(booking.getBookingCode()))
                .allMatch(summary -> !summary.canPay());
    }

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
