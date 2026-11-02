package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.request.VietQrTransactionSyncRequest;

public interface VietQrTransactionSyncService {
    String synchronize(VietQrTransactionSyncRequest request);
}
