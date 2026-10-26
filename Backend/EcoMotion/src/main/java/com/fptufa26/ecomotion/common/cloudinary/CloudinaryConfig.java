package com.fptufa26.ecomotion.common.cloudinary;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Value("${app.cloudinary.url:${CLOUDINARY_URL:}}")
    private String cloudinaryUrl;

    @Value("${app.cloudinary.cloud-name:demo}")
    private String cloudName;

    @Value("${app.cloudinary.api-key:123456789012345}")
    private String apiKey;

    @Value("${app.cloudinary.api-secret:abcdefghijklmnopqrstuvwxyz}")
    private String apiSecret;

    @Value("${app.cloudinary.mock:false}")
    private boolean mockMode;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (cloudinaryUrl != null && cloudinaryUrl.startsWith("cloudinary://")) {
            try {
                java.net.URI uri = java.net.URI.create(cloudinaryUrl);
                if (uri.getHost() != null && !uri.getHost().isBlank()) {
                    this.cloudName = uri.getHost();
                }
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    this.apiKey = parts[0];
                    this.apiSecret = parts[1];
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Bean
    public Cloudinary cloudinary() {
        if (cloudinaryUrl != null && cloudinaryUrl.startsWith("cloudinary://")) {
            return new Cloudinary(cloudinaryUrl);
        }
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        return new Cloudinary(config);
    }

    public boolean isMockOrDemo() {
        return mockMode
                || cloudName == null
                || cloudName.isBlank()
                || "demo".equalsIgnoreCase(cloudName.trim())
                || "123456789012345".equals(apiKey)
                || apiKey == null
                || apiKey.isBlank();
    }

    public String getCloudName() {
        return cloudName;
    }
}
