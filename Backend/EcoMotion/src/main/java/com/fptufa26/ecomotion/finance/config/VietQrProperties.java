package com.fptufa26.ecomotion.finance.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.vietqr")
@Getter
@Setter
public class VietQrProperties {
    private boolean enabled;
    private String environment = "sandbox";
    private String baseUrl = "https://dev.vietqr.org";
    private String username = "";
    private String password = "";
    private String callbackUsername = "";
    private String callbackPassword = "";
    private String callbackSecret = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
    private Duration tokenSkew = Duration.ofSeconds(30);
    private Duration callbackTokenTtl = Duration.ofMinutes(5);
}
