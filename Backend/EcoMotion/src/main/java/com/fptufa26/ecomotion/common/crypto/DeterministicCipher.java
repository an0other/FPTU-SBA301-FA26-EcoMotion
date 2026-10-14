package com.fptufa26.ecomotion.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Component
public class DeterministicCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec nonceKey;

    public DeterministicCipher(@Value("${app.crypto.secret}") String secret) {
        this.encryptionKey = new SecretKeySpec(sha256(secret + "|enc"), "AES");
        this.nonceKey = new SecretKeySpec(sha256(secret + "|iv"), "HmacSHA256");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = Arrays.copyOf(hmacSha256(plaintext), IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[IV_LENGTH + sealed.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(sealed, 0, payload, IV_LENGTH, sealed.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt value", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt value", ex);
        }
    }

    private byte[] hmacSha256(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(nonceKey);
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
