package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.TripEndConfirmation;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.TripEndConfirmationRepository;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripEndObjectionWindowTest {

    private static final Instant ENDED = Instant.parse("2026-11-09T12:25:00Z");

    private final TripEndConfirmationRepository histories = mock(TripEndConfirmationRepository.class);
    private final BookingPaymentLedgerService ledger = mock(BookingPaymentLedgerService.class);
    private final com.fptufa26.ecomotion.dispute.service.DisputeQueryService disputes =
            mock(com.fptufa26.ecomotion.dispute.service.DisputeQueryService.class);
    private final PaymentRequestService payments = mock(PaymentRequestService.class);
    private final com.fptufa26.ecomotion.finance.service.RefundEventService refunds =
            mock(com.fptufa26.ecomotion.finance.service.RefundEventService.class);
    private final AtomicReference<TripEndConfirmation> latest = new AtomicReference<>();
    private Booking booking;

    @SuppressWarnings("unchecked")
    private TripEndConfirmationService service(Instant now) {
        ObjectProvider<PaymentRequestService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(payments);
        ObjectProvider<com.fptufa26.ecomotion.finance.service.RefundEventService> refundProvider = mock(ObjectProvider.class);
        when(refundProvider.getObject()).thenReturn(refunds);
        ObjectProvider<com.fptufa26.ecomotion.dispute.service.DisputeQueryService> disputeProvider = mock(ObjectProvider.class);
        when(disputeProvider.getObject()).thenReturn(disputes);
        return new TripEndConfirmationService(histories, ledger, mock(BookingNotificationService.class), disputeProvider,
                Clock.fixed(now, ZoneOffset.UTC), provider, mock(jakarta.persistence.EntityManager.class), refundProvider);
    }

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setId(43L);
        booking.setDriverEndConfirmedAt(ENDED);
        booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        when(histories.save(any())).thenAnswer(inv -> { latest.set(inv.getArgument(0)); return inv.getArgument(0); });
        when(histories.findFirstByBooking_IdOrderByIdDesc(43L)).thenAnswer(inv -> Optional.ofNullable(latest.get()));
        when(disputes.findByBooking(43L)).thenReturn(List.of());
        when(payments.calculateRemainingAmount(43L)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void driverEndOpensTwelveHourWindowWithoutChangingStatus() {
        var svc = service(ENDED);
        svc.driverEnded(booking, 9L);
        assertThat(latest.get().getState()).isEqualTo("WAITING");
        assertThat(latest.get().getDeadline()).isEqualTo(ENDED.plusSeconds(12 * 3600));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        assertThat(svc.isEndSettled(43L)).isFalse();
        assertThat(svc.canObject(booking)).isTrue();
        assertThat(service(ENDED.plusSeconds(12 * 3600 + 1)).canObject(booking)).isFalse();
    }

    @Test
    void settledProviderSettlementCompletesOnlyAfterRenterConfirms() {
        var svc = service(ENDED.plusSeconds(600));
        svc.driverEnded(booking, 9L);
        booking.setSettlementRequestedAt(ENDED.plusSeconds(300));

        svc.completeIfReady(booking);
        assertThat(booking.getStatus()).as("cửa sổ còn mở thì chưa hoàn tất")
                .isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);

        svc.renterConfirm(booking, 5L);
        assertThat(latest.get().getConfirmationMethod()).isEqualTo("RENTER");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void openOverpaymentRefundHoldsCompletionUntilRefunded() {
        var svc = service(ENDED.plusSeconds(600));
        svc.driverEnded(booking, 9L);
        booking.setSettlementRequestedAt(ENDED.plusSeconds(300));
        when(refunds.hasOpenExcessRefunds(43L)).thenReturn(true);

        svc.renterConfirm(booking, 5L);
        assertThat(booking.getStatus()).as("khách chuyển dư chưa được hoàn thì chưa hoàn tất")
                .isEqualTo(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);

        when(refunds.hasOpenExcessRefunds(43L)).thenReturn(false);
        svc.completeIfReady(booking);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void expiredWindowAutoConfirmsAndCompletes() {
        service(ENDED).driverEnded(booking, 9L);
        booking.setSettlementRequestedAt(ENDED.plusSeconds(300));
        when(histories.findByStateAndDeadlineLessThanEqual(any(), any())).thenAnswer(inv -> List.of(latest.get()));
        when(ledger.requireBookingForUpdate(43L)).thenReturn(booking);
        latest.get().setBooking(booking);
        latest.get().setId(1L);

        service(ENDED.plusSeconds(12 * 3600 + 5)).confirmExpired();

        assertThat(latest.get().getState()).isEqualTo("CONFIRMED");
        assertThat(latest.get().getConfirmationMethod()).isEqualTo("AUTO");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }
}
