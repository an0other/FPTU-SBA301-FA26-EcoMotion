package com.fptufa26.ecomotion.finance.job;

import com.fptufa26.ecomotion.finance.service.implement.RefundReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundReceiptJob {
    private final RefundReceiptService receipts;

    @Scheduled(fixedDelayString = "${app.refund.receipt-check-ms:10000}", initialDelayString = "${app.refund.receipt-check-ms:10000}")
    public void confirmExpired() { receipts.confirmExpired(); }
}
