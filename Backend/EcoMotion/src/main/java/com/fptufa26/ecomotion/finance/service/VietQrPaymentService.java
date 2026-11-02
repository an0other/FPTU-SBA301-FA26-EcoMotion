package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;

public interface VietQrPaymentService {
    PaymentRequestResponse create(Long paymentRequestId, Long userId);
    PaymentRequestResponse status(String paymentCode, Long userId);
    org.springframework.data.domain.Page<com.fptufa26.ecomotion.finance.dto.response.VietQrCallbackResponse> callbacks(Long userId, int page);
}
