package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.service.*;
import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.dispute.service.DisputeQueryService;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.entity.*;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.repository.*;
import com.fptufa26.ecomotion.finance.service.PaymentCodeGenerator;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class YearlyPaymentAmountsTest {
    private PaymentRequestServiceImpl service;
    private PaymentRequest request;
    private PaymentRequestRepository requests;
    private com.fptufa26.ecomotion.finance.service.RefundEventService refunds;
    private final List<PaymentTransaction> transactions = new ArrayList<>();

    @BeforeEach
    void setup() {
        TransactionSynchronizationManager.initSynchronization();
        service = mock(PaymentRequestServiceImpl.class, CALLS_REAL_METHODS);
        requests = mock(PaymentRequestRepository.class);
        refunds = mock(com.fptufa26.ecomotion.finance.service.RefundEventService.class);
        ReflectionTestUtils.setField(service,"refundEventService",refunds);
        ReflectionTestUtils.setField(service,"financialAudit",mock(com.fptufa26.ecomotion.finance.service.FinancialAuditService.class));
        var ledger = mock(BookingPaymentLedgerService.class);
        var users = mock(UserService.class);
        var evidence = mock(PaymentEvidenceRepository.class);
        var accounts = mock(ProviderBankAccountRepository.class);
        var files = mock(FileStorageService.class);
        var codes = mock(PaymentCodeGenerator.class);
        Map.of("paymentRequestRepository", requests, "bookingPaymentLedgerService", ledger,
                "userService", users, "paymentEvidenceRepository", evidence, "providerBankAccountRepository", accounts,
                "fileStorageService", files, "paymentCodeGenerator", codes,
                "entityManager", mock(jakarta.persistence.EntityManager.class),
                "bookingNotificationService", mock(BookingNotificationService.class),
                "disputeQueryService", mock(DisputeQueryService.class)).forEach((key,value) -> ReflectionTestUtils.setField(service,key,value));
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"),ZoneOffset.UTC));
        var renterUser = new User(); renterUser.setId(1L);
        var renter = new Renter(); renter.setUser(renterUser);
        var owner = new User(); owner.setId(2L);
        var provider = new Provider(); provider.setId(4L); provider.setUser(owner);
        var car = new Vehicle(); car.setProvider(provider);
        var plan = new RentalPlan(); plan.setPlanType(PlanType.YEARLY);
        var booking = new Booking(); booking.setId(58L); booking.setBookingCode("YEAR-58");
        booking.setVehicle(car); booking.setRenter(renter); booking.setPlan(plan);
        booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT); booking.setActualStartAt(Instant.parse("2025-11-12T00:00:00Z")); booking.setActualEndAt(Instant.now());
        booking.setRenterEndConfirmedAt(Instant.now());
        var account = new ProviderBankAccount(); account.setId(3L);
        request = new PaymentRequest(); request.setId(146L); request.setBooking(booking);
        request.setPaymentType(PaymentRequestType.ADJUSTMENT); request.setStatus(PaymentRequestStatus.PENDING);
        request.setAmount(new BigDecimal("255100000")); request.setPaidAmount(BigDecimal.ZERO); request.setReceivingBankAccount(account);
        when(requests.findById(146L)).thenReturn(Optional.of(request));
        when(requests.findByIdForUpdate(146L)).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(i -> i.getArgument(0));
        when(requests.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var deposit = new PaymentRequest(); deposit.setSecurityDeposit(true); deposit.setStatus(PaymentRequestStatus.CONFIRMED);
        when(requests.findByBooking_IdAndPaymentType(58L,PaymentRequestType.DEPOSIT)).thenReturn(Optional.of(deposit));
        when(users.findById(1L)).thenReturn(Optional.of(renterUser));
        when(users.findProviderByUserId(2L)).thenReturn(Optional.of(provider));
        when(accounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
        when(ledger.requireBookingForUpdate(58L)).thenReturn(booking);
        when(ledger.listTransactions(58L)).thenAnswer(i -> List.copyOf(transactions));
        when(ledger.findTransactionByManualConfirmationId(anyString())).thenAnswer(i -> transactions.stream().filter(tx -> i.getArgument(0).equals(tx.getManualConfirmationId())).findFirst());
        when(ledger.saveTransaction(any())).thenAnswer(i -> { PaymentTransaction tx = i.getArgument(0); tx.setId((long)transactions.size()+1); transactions.add(tx); return tx; });
        when(codes.generatePaymentCode(anyString())).thenAnswer(i -> "TX" + transactions.size());
        when(evidence.save(any())).thenAnswer(i -> { PaymentEvidence e = i.getArgument(0); e.setId((long)request.getEvidences().size()+1); return e; });
        when(files.store(anyString(),anyLong(),anyString(),any(),anyString())).thenReturn("payment-evidence/1/payment-proof-146-test.png");
    }

    @AfterEach
    void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void multipartKeepsDeclaredAmountAndPartialAdjustmentCanBeCollectedInSeparateTransfers() {
        var file = new MockMultipartFile("file","proof.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10});
        var proof = service.submitPaymentProof(146L,1L,file,new BigDecimal("25000000"),"REF-25",null,"first");
        assertEquals(0,new BigDecimal("25000000").compareTo(proof.transferredAmount()));
        assertEquals(BigDecimal.ZERO,request.getPaidAmount());
        assertEquals(PaymentRequestStatus.PROOF_SUBMITTED,request.getStatus());
        var partial = service.confirmPayment(146L,2L,new ConfirmPaymentRequest(new BigDecimal("25000000"),"BANK-25",null,"CONF-25"));
        assertEquals(PaymentRequestStatus.PARTIALLY_PAID,partial.status());
        assertEquals(0,new BigDecimal("230100000").compareTo(partial.remainingAmount()));
        assertEquals(0,new BigDecimal("25000000").compareTo(transactions.get(0).getAmount()));
        service.confirmPayment(146L,2L,new ConfirmPaymentRequest(new BigDecimal("25000000"),"BANK-25",null,"CONF-25"));
        assertEquals(1,transactions.size());
        var full = service.confirmPayment(146L,2L,new ConfirmPaymentRequest(new BigDecimal("230100000"),"BANK-REST",null,"CONF-REST"));
        assertEquals(PaymentRequestStatus.CONFIRMED,full.status());
        assertEquals(0,BigDecimal.ZERO.compareTo(full.remainingAmount()));
        assertEquals(BookingStatus.AWAITING_FINAL_PAYMENT,request.getBooking().getStatus());
        assertEquals(2,transactions.size());
    }

    @Test
    void yearlyAmountsMustBeExplicitAndProviderReceiptIsIndependentFromClaim() {
        assertThrows(InvalidPaymentOperationException.class,() -> service.submitPaymentProof(146L,1L,null,null,"REF",null,null));
        assertThrows(InvalidPaymentOperationException.class,() -> service.confirmPayment(146L,2L,new ConfirmPaymentRequest(null,"BANK",null,"CONF")));
        var proof = service.submitPaymentProof(146L,1L,null,new BigDecimal("25000000"),"REF",null,null);
        service.confirmPayment(146L,2L,new ConfirmPaymentRequest(new BigDecimal("24000000"),"BANK-24",null,"CONF-24"));
        assertEquals(0,new BigDecimal("25000000").compareTo(proof.transferredAmount()));
        assertEquals(0,new BigDecimal("24000000").compareTo(request.getPaidAmount()));
        assertEquals(PaymentRequestStatus.PARTIALLY_PAID,request.getStatus());
    }
    @Test
    void rejectingNewEvidencePreservesEarlierPartialPaymentAndAllowsResubmission() {
        service.confirmPayment(146L,2L,new ConfirmPaymentRequest(new BigDecimal("25000000"),"BANK-25",null,"CONF-25"));
        var proof = service.submitPaymentProof(146L,1L,null,new BigDecimal("230100000"),"WRONG-PROOF",null,null);
        service.rejectYearlyEvidence(146L,proof.id(),2L,new com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest("Không tìm thấy mã trên sao kê, gửi lại biên lai"));
        assertEquals(PaymentRequestStatus.PARTIALLY_PAID,request.getStatus());
        assertEquals(0,new BigDecimal("25000000").compareTo(request.getPaidAmount()));
        assertEquals(PaymentEvidenceStatus.REJECTED,request.getEvidences().get(0).getStatus());
        assertEquals(1,transactions.size());
        assertThrows(com.fptufa26.ecomotion.common.exception.ConflictException.class,
                () -> service.rejectYearlyEvidence(146L,proof.id(),2L,new com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest("retry")));
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"),ZoneOffset.UTC));
        service.submitPaymentProof(146L,1L,null,new BigDecimal("230100000"),"NEW-PROOF",null,null);
        assertEquals(Instant.parse("2026-11-12T03:00:00Z"),request.getReconciliationDeadline());
        assertEquals(PaymentRequestStatus.PROOF_SUBMITTED,request.getStatus());
        assertThrows(com.fptufa26.ecomotion.common.exception.ConflictException.class,
                () -> service.rejectYearlyEvidence(146L,proof.id(),2L,new com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest("old proof")));
    }

    @Test
    void overpaymentCreatesOnlyExcessRefundOnTheOriginalReceiptAndRetryDoesNotDuplicateIt() {
        var result = service.confirmPayment(146L,2L,new ConfirmPaymentRequest(new BigDecimal("300000000"),"BANK-300",null,"CONF-300"));
        assertEquals(PaymentRequestStatus.CONFIRMED,result.status());
        assertEquals(0,new BigDecimal("255100000").compareTo(request.getPaidAmount()));
        assertEquals(0,new BigDecimal("300000000").compareTo(transactions.get(0).getAmount()));
        service.confirmPayment(146L,2L,new ConfirmPaymentRequest(new BigDecimal("300000000"),"BANK-300",null,"CONF-300"));
        verify(refunds,times(1)).reserveOnTransaction(eq(58L),eq("OVERPAY:1"),eq(new BigDecimal("44900000")),anyString(),any(User.class),eq(1L));
        assertEquals(1,transactions.size());
    }

    @Test
    void yearlyEvidenceEndpointDoesNotChangeLegacyRejectionForOtherPlans() {
        request.getBooking().getPlan().setPlanType(PlanType.MONTHLY);
        request.setStatus(PaymentRequestStatus.PROOF_SUBMITTED); request.setPaidAmount(new BigDecimal("25000000"));
        assertThrows(InvalidPaymentOperationException.class,() -> service.rejectYearlyEvidence(146L,1L,2L,
                new com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest("invalid proof")));
        assertThrows(com.fptufa26.ecomotion.common.exception.ConflictException.class,() -> service.rejectPayment(146L,2L,
                new com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest("invalid proof")));
        assertEquals(PaymentRequestStatus.PROOF_SUBMITTED,request.getStatus());
        assertEquals(0,new BigDecimal("25000000").compareTo(request.getPaidAmount()));
        assertTrue(transactions.isEmpty());
    }

    @Test
    void refundPartsKeepEachBankReferenceAndFitExistingIdempotencyColumn() {
        request.getBooking().getRenter().getUser().setStatus(com.fptufa26.ecomotion.user.enums.UserStatus.ACTIVE);
        String first = UUID.randomUUID().toString();
        service.recordYearlyRefundPart(58L, BigDecimal.ZERO, new BigDecimal("20000000"), "BANK-A", "proof-a", 1L, false, first);
        service.recordYearlyRefundPart(58L, BigDecimal.ZERO, new BigDecimal("20000000"), "BANK-A", "proof-a", 1L, false, first);
        service.recordYearlyRefundPart(58L, BigDecimal.ZERO, new BigDecimal("28500000"), "BANK-B", "proof-b", 1L, false, UUID.randomUUID().toString());
        assertEquals(2, transactions.size());
        assertEquals(new BigDecimal("20000000"), transactions.get(0).getAmount());
        assertEquals(new BigDecimal("28500000"), transactions.get(1).getAmount());
        assertTrue(transactions.get(0).getProcessingNote().contains("BANK-A"));
        assertTrue(transactions.get(1).getProcessingNote().contains("BANK-B"));
        assertTrue(transactions.stream().allMatch(t -> t.getManualConfirmationId().length() <= 64));
    }

    @Test
    void firstMonthBeforeHandoverIsDueBeforeReceiptNotYesterday() {
        request.setPaymentType(PaymentRequestType.MONTHLY_INSTALLMENT); request.setInstallmentNumber(1);
        request.getBooking().setActualStartAt(null);
        var clock = (Clock) ReflectionTestUtils.getField(service, "clock");
        var today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        request.setPeriodStart(today); request.setPeriodEnd(today.plusMonths(1));
        when(requests.findByBooking_BookingCodeOrderByCreatedAtDesc("YEAR-58")).thenReturn(List.of(request));
        doReturn(List.of()).when(service).getPaymentRequestsByBookingCode("YEAR-58", 1L);
        var first = service.yearlyPayments(1L, "YEAR-58").getFirst();
        assertNull(first.dueOn()); assertFalse(first.overdue());
        request.setInstallmentNumber(2);
        var next = service.yearlyPayments(1L, "YEAR-58").getFirst();
        assertEquals(today.minusDays(1), next.dueOn()); assertTrue(next.overdue());
    }

}
