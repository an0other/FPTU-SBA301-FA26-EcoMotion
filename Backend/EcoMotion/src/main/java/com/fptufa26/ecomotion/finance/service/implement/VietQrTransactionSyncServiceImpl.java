package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.finance.dto.request.VietQrTransactionSyncRequest;
import com.fptufa26.ecomotion.finance.service.VietQrPaymentReconciliationService;
import com.fptufa26.ecomotion.finance.service.VietQrTransactionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VietQrTransactionSyncServiceImpl implements VietQrTransactionSyncService {
    private final VietQrPaymentReconciliationService reconciliation;

    @Override
    public String synchronize(VietQrTransactionSyncRequest request) {
        try {
            return reconciliation.reconcile(request);
        } catch (DataIntegrityViolationException duplicateRace) {
            return reconciliation.reconcile(request);
        }
    }
}
