package com.fptufa26.ecomotion.verification.identity;

import com.fptufa26.ecomotion.verification.identity.service.implement.IdentityFingerprintServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityFingerprintServiceTest {

    private final IdentityFingerprintServiceImpl service = new IdentityFingerprintServiceImpl(
            "identity-test-secret-0123456789abcdef", 1);

    @Test
    void normalizesSpacingWithoutRemovingLeadingZeroes() {
        assertEquals("001234567890", service.normalizeCccdNumber(" 001 234 567 890\t"));
        assertEquals(service.fingerprint("001234567890"), service.fingerprint("001 234 567 890"));
    }

    @Test
    void matchesIndependentHmacFixture() {
        assertEquals("e501788a9984927fa85db7db0f9ea077580dd498f4effee4f4a2cd33fd3e1377",
                service.fingerprint("001234567890"));
        assertEquals(1, service.keyVersion());
    }

    @Test
    void distinguishesDifferentNumbersAndKeys() {
        assertNotEquals(service.fingerprint("001234567890"), service.fingerprint("001234567891"));
        var otherKey = new IdentityFingerprintServiceImpl("different-test-secret-0123456789abcdef", 2);
        assertNotEquals(service.fingerprint("001234567890"), otherKey.fingerprint("001234567890"));
    }

    @Test
    void rejectsMalformedNumbersInsteadOfRepairingOcrErrors() {
        for (String value : new String[]{"", "123456789", "00123456789O", "0012345678901", "００１２３４５６７８９０"}) {
            assertThrows(IllegalArgumentException.class, () -> service.fingerprint(value));
        }
        assertThrows(IllegalArgumentException.class, () -> service.fingerprint(null));
    }

    @Test
    void refusesToFingerprintWithoutAnIndependentConfiguredKey() {
        var unconfigured = new IdentityFingerprintServiceImpl("", 1);
        assertThrows(IllegalStateException.class, () -> unconfigured.fingerprint("001234567890"));
        var badVersion = new IdentityFingerprintServiceImpl("identity-test-secret-0123456789abcdef", 0);
        assertThrows(IllegalStateException.class, badVersion::keyVersion);
    }
}
