package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.verification.identity.service.IdentityFingerprintService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Service
public class IdentityFingerprintServiceImpl implements IdentityFingerprintService {

    private final byte[] secret;
    private final int keyVersion;

    public IdentityFingerprintServiceImpl(
            @Value("${app.identity.fingerprint-secret:}") String secret,
            @Value("${app.identity.fingerprint-key-version:1}") int keyVersion) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.keyVersion = keyVersion;
    }

    @Override
    public String normalizeCccdNumber(String cccdNumber) {
        if (cccdNumber == null) {
            throw new IllegalArgumentException("CCCD number is required");
        }
        String normalized = cccdNumber.replaceAll("\\s+", "");
        if (!normalized.matches("[0-9]{12}")) {
            throw new IllegalArgumentException("CCCD number must contain exactly 12 digits");
        }
        return normalized;
    }

    @Override
    public String fingerprint(String cccdNumber) {
        String normalized = normalizeCccdNumber(cccdNumber);
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not create identity fingerprint", exception);
        }
    }

    @Override
    public int keyVersion() {
        requireConfigured();
        return keyVersion;
    }

    private void requireConfigured() {
        if (secret.length < 32 || keyVersion < 1) {
            throw new IllegalStateException(
                    "Configure an identity fingerprint secret of at least 32 bytes and a positive key version");
        }
    }
}
