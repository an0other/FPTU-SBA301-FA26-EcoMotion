package com.fptufa26.ecomotion.finance.service.gateway;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayResolver {

    private final Map<PaymentMethod, PaymentGatewayStrategy> strategies = new EnumMap<>(PaymentMethod.class);
    private final PaymentMethod defaultMethod;

    public PaymentGatewayResolver(
            List<PaymentGatewayStrategy> gatewayStrategies,
            @Value("${app.payment.default-gateway:VNPAY}") String defaultGateway) {
        if (gatewayStrategies != null) {
            for (PaymentGatewayStrategy strategy : gatewayStrategies) {
                strategies.put(strategy.getPaymentMethod(), strategy);
            }
        }
        PaymentMethod resolvedDefault;
        try {
            resolvedDefault = PaymentMethod.valueOf(defaultGateway.trim().toUpperCase());
        } catch (Exception ex) {
            resolvedDefault = PaymentMethod.VNPAY;
        }
        this.defaultMethod = resolvedDefault;
    }

    public PaymentGatewayStrategy resolve(PaymentMethod method) {
        PaymentMethod target = method != null ? method : defaultMethod;
        PaymentGatewayStrategy strategy = strategies.get(target);
        if (strategy == null) {
            throw new IllegalArgumentException("Không tìm thấy cấu hình cổng thanh toán cho phương thức: " + target);
        }
        return strategy;
    }

    public PaymentGatewayStrategy resolveDefault() {
        return resolve(defaultMethod);
    }
}
