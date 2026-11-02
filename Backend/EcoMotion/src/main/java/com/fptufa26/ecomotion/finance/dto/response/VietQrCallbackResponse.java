package com.fptufa26.ecomotion.finance.dto.response;

import java.time.Instant;

public record VietQrCallbackResponse(String transactionId, String orderId, Long paymentRequestId,
                                    String outcome, Instant receivedAt) {}
