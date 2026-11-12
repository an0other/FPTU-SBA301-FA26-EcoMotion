package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YearlyRefundGuardTest {
    @Test
    void yearlyGuardWaitsForReceiptWithoutChangingCommonRefundRule() {
        var service = mock(RefundEventServiceImpl.class,CALLS_REAL_METHODS);
        var repo = mock(RefundRequestRepository.class);
        ReflectionTestUtils.setField(service,"refundRequestRepository",repo);
        var refund = new RefundRequest(); refund.setStatus(RefundStatus.REFUNDED); refund.setReceiptStatus("WAITING");
        when(repo.findByBooking_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(refund));
        assertTrue(service.hasUnsettledYearlyRefunds(1L));
        assertFalse(service.hasOpenRefunds(1L));
        refund.setReceiptStatus("CONFIRMED"); assertFalse(service.hasUnsettledYearlyRefunds(1L));
        refund.setReceiptStatus("REPORTED_NOT_RECEIVED"); assertTrue(service.hasUnsettledYearlyRefunds(1L));
        refund.setReceiptStatus(null); refund.setStatus(RefundStatus.FAILED); assertTrue(service.hasUnsettledYearlyRefunds(1L));
    }

    @Test
    void completedSecurityDepositRefundReducesRemainingRefundSource() {
        var service = mock(RefundEventServiceImpl.class,CALLS_REAL_METHODS);
        var repo = mock(RefundRequestRepository.class);
        var ledger = mock(BookingPaymentLedgerService.class);
        ReflectionTestUtils.setField(service,"refundRequestRepository",repo);
        ReflectionTestUtils.setField(service,"ledger",ledger);
        var received = new PaymentTransaction(); received.setId(1L); received.setType(PaymentType.SECURITY_DEPOSIT);
        received.setDirection(PaymentDirection.IN); received.setStatus(PaymentStatus.PAID); received.setAmount(new BigDecimal("50000000"));
        var refunded = new PaymentTransaction(); refunded.setId(2L); refunded.setType(PaymentType.SECURITY_DEPOSIT_REFUND);
        refunded.setDirection(PaymentDirection.OUT); refunded.setStatus(PaymentStatus.PAID); refunded.setAmount(new BigDecimal("50000000"));
        when(ledger.listTransactions(1L)).thenReturn(List.of(received,refunded));
        assertEquals(0,BigDecimal.ZERO.compareTo(service.remainingReceived(1L)));
    }
}
