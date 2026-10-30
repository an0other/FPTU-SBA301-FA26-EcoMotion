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

    private String tmnCode = "";
    private String hashSecret = "";
    private String paymentUrl = "";
    private String returnUrl = "";
    private String ipnUrl = "";
    private String apiUrl = "";
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
    private String currCode = "VND";
    private boolean reconciliationEnabled = true;
    private int reconciliationBatchSize = 25;
    private String serverIp = "127.0.0.1";
    private java.time.Duration queryTimeout = java.time.Duration.ofSeconds(5);
    private java.time.Duration queryInterval = java.time.Duration.ofSeconds(60);
    private String mode = "SANDBOX";
}
