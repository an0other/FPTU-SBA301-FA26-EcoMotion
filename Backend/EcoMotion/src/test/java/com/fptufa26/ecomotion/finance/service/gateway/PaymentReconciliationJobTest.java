package com.fptufa26.ecomotion.finance.service.gateway;

import com.fptufa26.ecomotion.booking.dto.response.PaymentReconciliationTarget;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.service.VnpayService;
import com.fptufa26.ecomotion.finance.service.implement.PaymentReconciliationServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class PaymentReconciliationJobTest {
    @Test void disabledJobDoesNotReadBookingsOrContactGateway() {
        var bookings = mock(BookingQueryService.class); var gateway = mock(VnpayService.class);
        var config = new VnpayConfig(); config.setReconciliationEnabled(false);
        new PaymentReconciliationServiceImpl(bookings, gateway, config).reconcilePending();
        verifyNoInteractions(bookings, gateway);
    }

    @Test void failedGatewayReadDoesNotStarveFollowingPaymentsInBoundedBatch() {
        var bookings = mock(BookingQueryService.class); var gateway = mock(VnpayService.class);
        var config = new VnpayConfig(); config.setReconciliationBatchSize(2);
        when(bookings.pendingGatewayPayments(2)).thenReturn(List.of(
                new PaymentReconciliationTarget("BOOKING-1", "REF-1"), new PaymentReconciliationTarget("BOOKING-2", "REF-2")));
        when(gateway.queryTransaction("BOOKING-1", "REF-1")).thenThrow(new IllegalStateException("Gateway unavailable"));
        new PaymentReconciliationServiceImpl(bookings, gateway, config).reconcilePending();
        verify(gateway).queryTransaction("BOOKING-1", "REF-1");
        verify(gateway).queryTransaction("BOOKING-2", "REF-2");
        verifyNoMoreInteractions(gateway);
    }
}
