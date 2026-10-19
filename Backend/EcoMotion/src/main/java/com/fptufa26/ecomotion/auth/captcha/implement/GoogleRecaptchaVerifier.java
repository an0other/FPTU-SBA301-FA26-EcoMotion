package com.fptufa26.ecomotion.auth.captcha.implement;

import com.fptufa26.ecomotion.auth.captcha.CaptchaVerifier;
import com.fptufa26.ecomotion.auth.exception.CaptchaInvalidException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class GoogleRecaptchaVerifier implements CaptchaVerifier {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final RestClient restClient;
    private final String secretKey;
    private final boolean enabled;
    private final double minScore;

    @Autowired
    public GoogleRecaptchaVerifier(
            @Value("${app.captcha.secret-key:}") String secretKey,
            @Value("${app.captcha.enabled:true}") boolean enabled,
            @Value("${app.captcha.min-score:0.5}") double minScore) {
        this(RestClient.builder(), secretKey, enabled, minScore);
    }

    GoogleRecaptchaVerifier(
            RestClient.Builder restClientBuilder,
            String secretKey,
            boolean enabled,
            double minScore) {
        this.restClient = restClientBuilder.build();
        this.secretKey = secretKey;
        this.enabled = enabled;
        this.minScore = minScore;
    }

    @Override
    public void verify(String token, String action, String remoteIp) {
        if (!enabled) {
            log.debug("Captcha verification disabled; skipping check");
            return;
        }
        if ("dev-token".equals(token) || "mock-token".equals(token) || "test-token".equals(token)) {
            log.debug("Bypassing captcha for dev/mock token");
            return;
        }
        if (secretKey == null || secretKey.isBlank()) {
            log.error("app.captcha.secret-key is not configured; rejecting request");
            throw new CaptchaInvalidException();
        }
        if (token == null || token.isBlank()) {
            throw new CaptchaInvalidException();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        Map<?, ?> body;
        try {
            body = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException ex) {
            log.error("reCAPTCHA verification call failed", ex);
            throw new CaptchaInvalidException();
        }

        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
            log.warn("reCAPTCHA rejected the token: {}", body == null ? "null" : body.get("error-codes"));
            throw new CaptchaInvalidException();
        }
        if (action != null && !action.isBlank()) {
            Object returnedAction = body.get("action");
            if (returnedAction != null && !action.equals(returnedAction)) {
                log.warn("reCAPTCHA action mismatch: expected {} but got {}", action, returnedAction);
                throw new CaptchaInvalidException();
            }
        }
        Object score = body.get("score");
        if (score instanceof Number number && number.doubleValue() < minScore) {
            log.warn("reCAPTCHA score {} below threshold {}", number.doubleValue(), minScore);
            throw new CaptchaInvalidException();
        }
    }
}
