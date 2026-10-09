package com.fptufa26.ecomotion.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicCipherTest {

    private static final String SECRET = "unit-test-crypto-secret-0123456789-32bytes";

    private final DeterministicCipher cipher = new DeterministicCipher(SECRET);

    @Test
    @DisplayName("encrypt then decrypt returns the original value")
    void roundTrip() {
        String phone = "0901234567";

        String encrypted = cipher.encrypt(phone);

        assertThat(encrypted).isNotEqualTo(phone);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(phone);
    }

    @Test
    @DisplayName("encryption is deterministic so unique constraints keep working")
    void encryptionIsDeterministic() {
        assertThat(cipher.encrypt("0901234567")).isEqualTo(cipher.encrypt("0901234567"));
    }

    @Test
    @DisplayName("different values produce different ciphertext")
    void differentValuesDiffer() {
        assertThat(cipher.encrypt("0901234567")).isNotEqualTo(cipher.encrypt("0901234568"));
    }

    @Test
    @DisplayName("CCCD numbers round-trip too")
    void cccdRoundTrip() {
        String cccd = "079123456789";

        assertThat(cipher.decrypt(cipher.encrypt(cccd))).isEqualTo(cccd);
    }

    @Test
    @DisplayName("null and empty values pass through untouched")
    void nullAndEmptyPassThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.encrypt("")).isEmpty();
        assertThat(cipher.decrypt("")).isEmpty();
    }

    @Test
    @DisplayName("a different key cannot decrypt the value")
    void wrongKeyCannotDecrypt() {
        String encrypted = cipher.encrypt("0901234567");
        DeterministicCipher other = new DeterministicCipher("another-secret-key-0123456789-32bytes");

        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }
}
