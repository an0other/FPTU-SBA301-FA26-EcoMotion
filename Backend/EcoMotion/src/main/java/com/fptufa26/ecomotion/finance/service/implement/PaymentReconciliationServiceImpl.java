package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.service.PaymentReconciliationService;
import com.fptufa26.ecomotion.finance.service.VnpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {
    private final BookingQueryService bookings;
    private final VnpayService vnpay;
    private final VnpayConfig config;

    @Override
    public void reconcilePending() {
        if (!config.isReconciliationEnabled()) return;
        for (var payment : bookings.pendingGatewayPayments(config.getReconciliationBatchSize())) {
            try { vnpay.queryTransaction(payment.bookingCode(), payment.transactionReference()); }
            catch (RuntimeException ex) { log.warn("Pending gateway reconciliation failed: {}", ex.getClass().getSimpleName()); }
        }
    }
}
