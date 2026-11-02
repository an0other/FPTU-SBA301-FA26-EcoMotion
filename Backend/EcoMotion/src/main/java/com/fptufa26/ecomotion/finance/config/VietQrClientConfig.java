package com.fptufa26.ecomotion.finance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration
public class VietQrClientConfig {
    @Bean
    public RestClient vietQrRestClient(VietQrProperties properties, org.springframework.core.env.Environment environment) {
        if (properties.isEnabled()) {
            if (java.util.Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"))
                    && !"production".equals(properties.getEnvironment()))
                throw new IllegalStateException("VietQR Sandbox is forbidden in production");
            String expected = switch (properties.getEnvironment()) {
                case "sandbox" -> "https://dev.vietqr.org";
                case "production" -> "https://api.vietqr.org";
                default -> throw new IllegalStateException("Unknown VietQR environment");
            };
            if (!expected.equals(properties.getBaseUrl())) throw new IllegalStateException("VietQR environment and host do not match");
        }
        var client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }
}
