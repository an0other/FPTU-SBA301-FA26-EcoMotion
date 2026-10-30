package com.fptufa26.ecomotion.finance.service.gateway;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlCommand;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlResult;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentVerificationResult;
import com.fptufa26.ecomotion.finance.service.gateway.implement.VnpayPaymentGatewayStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayStrategyUnitTest {

    private VnpayPaymentGatewayStrategy vnpayStrategy;
    private PaymentGatewayResolver resolver;

    @BeforeEach
    void setUp() {
        VnpayConfig vnpayConfig = new VnpayConfig();
        vnpayConfig.setPaymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnpayConfig.setTmnCode("TEST_TMN");
        vnpayConfig.setHashSecret("TEST_SECRET_KEY_12345678901234567890");
        vnpayConfig.setReturnUrl("https://ecomotion.vn/vnpay-return");
        vnpayConfig.setVersion("2.1.0");
        vnpayConfig.setCommand("pay");
        vnpayConfig.setOrderType("other");
        vnpayConfig.setCurrCode("VND");

        vnpayStrategy = new VnpayPaymentGatewayStrategy(vnpayConfig);
        resolver = new PaymentGatewayResolver(List.of(vnpayStrategy), "VNPAY");
    }

    @Test
    @DisplayName("PaymentGatewayResolver successfully resolves default VNPAY gateway")
    void resolver_resolvesDefaultGateway() {
        PaymentGatewayStrategy strategy = resolver.resolveDefault();
        assertThat(strategy).isNotNull();
        assertThat(strategy.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
    }

    @Test
    @DisplayName("PaymentGatewayResolver successfully resolves gateway by PaymentMethod")
    void resolver_resolvesByMethod() {
        PaymentGatewayStrategy strategy = resolver.resolve(PaymentMethod.VNPAY);
        assertThat(strategy).isNotNull();
        assertThat(strategy.getPaymentMethod()).isEqualTo(PaymentMethod.VNPAY);
    }

    @Test
    @DisplayName("PaymentGatewayResolver throws IllegalArgumentException for unconfigured payment method")
    void resolver_throwsForUnconfiguredMethod() {
        assertThatThrownBy(() -> resolver.resolve(PaymentMethod.CASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CASH");
    }

    @Test
    @DisplayName("VnpayPaymentGatewayStrategy generates valid payment URL with required params and signature")
    void vnpayStrategy_buildsPaymentUrl() {
        Instant now = Instant.now();
        PaymentUrlCommand command = new PaymentUrlCommand(
                "BK-2026-001",
                "1001",
                new BigDecimal("150000"),
                "Thanh toan dat xe BK-2026-001",
                "127.0.0.1",
                "vn",
                null,
                now,
                now.plus(Duration.ofMinutes(15))
        );

        PaymentUrlResult result = vnpayStrategy.generatePaymentUrl(command);

        assertThat(result).isNotNull();
        assertThat(result.paymentUrl()).isNotBlank();
        assertThat(result.paymentUrl()).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(result.paymentUrl()).contains("vnp_TmnCode=TEST_TMN");
        assertThat(result.paymentUrl()).contains("vnp_Amount=15000000");
        assertThat(result.paymentUrl()).contains("vnp_TxnRef=1001");
        assertThat(result.paymentUrl()).contains("vnp_SecureHash=");
    }

    @Test
    @DisplayName("VnpayPaymentGatewayStrategy verifies signature correctly for valid params")
    void vnpayStrategy_verifySignature_valid() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "TEST_TMN");
        params.put("vnp_TxnRef", "ref");
        params.put("vnp_Amount", "144000000");
        params.put("vnp_OrderInfo", "Thanh toán đơn thuê + mã & ký tự");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", com.fptufa26.ecomotion.finance.config.VnpayUtils.hmacSHA512(
                "TEST_SECRET_KEY_12345678901234567890", com.fptufa26.ecomotion.finance.config.VnpayUtils.buildHashData(params)));
        assertThat(vnpayStrategy.verifyCallback(params).validSignature()).isTrue();
        assertThat(vnpayStrategy.verifyCallback(params).success()).isTrue();
        params.put("vnp_Amount", "1");
        assertThat(vnpayStrategy.verifyCallback(params).validSignature()).isFalse();
    }

    @Test
    @DisplayName("VnpayPaymentGatewayStrategy rejects signature if tampered")
    void vnpayStrategy_verifySignature_tampered_fails() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "TEST_TMN");
        params.put("vnp_Amount", "10000000");
        params.put("vnp_TxnRef", "123");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "INVALID_HASH_VALUE_TAMPERED");

        PaymentVerificationResult verification = vnpayStrategy.verifyCallback(params);
        assertThat(verification.validSignature()).isFalse();
    }
}
