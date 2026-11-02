package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.response.PaymentReceiptResponse;

public interface PaymentReceiptService {
    PaymentReceiptResponse getReceipt(Long currentUserId, String reference);
}
