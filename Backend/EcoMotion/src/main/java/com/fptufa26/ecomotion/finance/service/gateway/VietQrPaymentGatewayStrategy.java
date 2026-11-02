package com.fptufa26.ecomotion.finance.service.gateway;

import java.math.BigDecimal;

public interface VietQrPaymentGatewayStrategy {
    DynamicQr generate(String orderId, String bankCode, String account, String holder, BigDecimal amount);
    record DynamicQr(String orderId, String bankCode, String account, BigDecimal amount,
                     String content, String qrCode, String qrLink) {}
}
