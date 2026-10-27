package com.fptufa26.ecomotion.finance.service.gateway;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlCommand;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlResult;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentVerificationResult;

import java.util.Map;

public interface PaymentGatewayStrategy {

    PaymentMethod getPaymentMethod();

    PaymentUrlResult generatePaymentUrl(PaymentUrlCommand command);

    PaymentVerificationResult verifyCallback(Map<String, String> params);

    PaymentVerificationResult verifyIpn(Map<String, String> params);
}
