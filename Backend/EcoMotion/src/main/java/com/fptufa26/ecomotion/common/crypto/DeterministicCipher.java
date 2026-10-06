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

/**
 * Reversible (two-way) AES-256-GCM cipher for short PII values — the phone number and the CCCD
 * number. Values are encrypted at rest and decrypted on read, so they stay usable for display and
 * for the activation flow.
 *
 * <p><b>Deterministic on purpose.</b> The same plaintext always produces the same ciphertext, which
 * is what keeps the database UNIQUE constraints ({@code users.phone}, {@code renter.cccd_no}) and
 * equality lookups working on the encrypted column. To stay safe despite the determinism, the GCM
 * nonce is derived from the plaintext with a separate key, so two different values can never share
 * a nonce. The trade-off is that equal values are detectable in the database — acceptable for these
 * fields, whose uniqueness is already exposed by the unique indexes.</p>
 *
 * <p>The key comes from {@code app.crypto.secret}. Rotating it makes every previously stored value
 * unreadable, so treat it like any other production secret.</p>
 */
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

    /** Encrypts a value; null/empty values are passed through untouched. */
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

    /** Decrypts a value previously produced by {@link #encrypt}; null/empty values pass through. */
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
