package com.fptufa26.ecomotion.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.implement.BookingHoldExpiryServiceImpl;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** A PENDING VNPAY payment blocks hold expiry only within its grace period. */
@ExtendWith(MockitoExtension.class)
class BookingHoldExpiryPendingVnpayTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Mock private BookingRepository bookingRepository;
    @Mock private ScheduleService scheduleService;
    @Mock private PaymentRequestService paymentRequestService;
    @Mock private PaymentTransactionRepository transactions;
    @Mock private BookingNotificationService notifications;

    @InjectMocks private BookingHoldExpiryServiceImpl service;

    private Booking booking;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "vnpayPendingGrace", Duration.ofMinutes(30));
        booking = new Booking();
        booking.setId(7L);
        booking.setStatus(BookingStatus.AWAITING_DEPOSIT);
        when(scheduleService.expiredHoldBookingIds(NOW)).thenReturn(List.of(7L));
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(paymentRequestService.findPaymentRequest(anyLong(), any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(paymentRequestService.expireDepositWithoutProof(7L)).thenReturn(true);
    }

    private PaymentTransaction pendingVnpay(Instant createdAt) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setMethod(PaymentMethod.VNPAY);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setCreatedAt(createdAt);
        return tx;
    }

    @Test
    void recentPendingVnpayPaymentKeepsDepositOpen() {
        when(transactions.findByBooking_Id(7L)).thenReturn(List.of(pendingVnpay(NOW.minus(Duration.ofMinutes(5)))));

        service.expireDueHolds(NOW);

        verify(paymentRequestService, never()).expireDepositWithoutProof(anyLong());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
    }

    @Test
    void stalePendingVnpayPaymentDoesNotBlockExpiry() {
        when(transactions.findByBooking_Id(7L)).thenReturn(List.of(pendingVnpay(NOW.minus(Duration.ofMinutes(40)))));

        service.expireDueHolds(NOW);

        verify(paymentRequestService).expireDepositWithoutProof(7L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
