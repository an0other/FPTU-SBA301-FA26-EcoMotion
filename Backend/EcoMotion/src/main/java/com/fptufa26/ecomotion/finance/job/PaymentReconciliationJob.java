package com.fptufa26.ecomotion.finance.job;

import com.fptufa26.ecomotion.finance.service.PaymentReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {
    private final PaymentReconciliationService reconciliation;

    @Scheduled(fixedDelayString = "${app.vnpay.reconciliation-delay-ms:60000}", initialDelayString = "${app.vnpay.reconciliation-delay-ms:60000}")
    public void reconcile() { reconciliation.reconcilePending(); }
}
