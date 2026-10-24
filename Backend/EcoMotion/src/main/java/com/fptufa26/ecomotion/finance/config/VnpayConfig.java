package com.fptufa26.ecomotion.finance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.vnpay")
@Getter
@Setter
public class VnpayConfig {

    private String tmnCode = "D60936ZG";
    private String hashSecret = "UHLVIUTFPXKYUNXOYPUPSBJRJAJAVMSN";
    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String returnUrl = "http://localhost:5173/payment/vnpay/callback";
    private String ipnUrl = "http://localhost:8080/api/v1/payment/vnpay/ipn";
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
    private String currCode = "VND";
    private String mode = "SANDBOX";
}
