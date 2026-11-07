package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.request.RefundReconcileRequest;
import java.util.List;

public interface RefundReconciliationService {
    void reconcile(Long adminId, Long refundId, RefundReconcileRequest request);
    List<FinancialAuditService.Entry> history(Long adminId, Long refundId);
}
