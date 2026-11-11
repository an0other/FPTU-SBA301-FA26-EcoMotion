package com.fptufa26.ecomotion.support;

import com.fptufa26.ecomotion.user.entity.ProviderApplication;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;
import com.fptufa26.ecomotion.verification.identity.enums.IdentityVerificationMethod;
import com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus;
import jakarta.persistence.EntityManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gives test users the identity state the rental rules require (UserServiceImpl#assertRentalAllowed and
 * #isProviderOperational): a VERIFIED, unexpired, non-demo {@link PersonIdentity} of their own and, for providers,
 * an APPROVED {@link ProviderApplication}. Must be called inside an open transaction after the user is persisted.
 */
public final class IdentityTestFixtures {

    private IdentityTestFixtures() {
    }

    /** Persists a fresh verified identity and links it to the user; providers also get an approved application. */
    public static User verify(EntityManager em, User user) {
        if (user.getRole() != Role.RENTER && user.getRole() != Role.PROVIDER) {
            return user;
        }
        user.setIdentity(verifiedIdentity(em, user.getFullName()));
        Instant now = Instant.now();
        if (user.getEmailVerifiedAt() == null) user.setEmailVerifiedAt(now);
        if (user.getPhoneVerifiedAt() == null) user.setPhoneVerifiedAt(now);
        if (user.getTermsAcceptedAt() == null) user.setTermsAcceptedAt(now);
        if (user.getRole() == Role.PROVIDER && em.find(ProviderApplication.class, user.getId()) == null) {
            ProviderApplication application = new ProviderApplication();
            application.setUserId(user.getId());
            application.setStatus(ProviderApplicationStatus.APPROVED);
            application.setSubmittedAt(now);
            application.setReviewedAt(now);
            application.setSubmittedIdentityId(user.getIdentity().getId());
            em.persist(application);
        }
        return user;
    }

    public static PersonIdentity verifiedIdentity(EntityManager em, String fullName) {
        String cccd = String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
        PersonIdentity identity = new PersonIdentity();
        identity.setCccdNo(cccd);
        identity.setCccdFingerprint(sha256Hex(cccd + ":" + System.nanoTime()));
        identity.setFingerprintKeyVersion(1);
        identity.setFullName(fullName == null ? "Test identity" : fullName);
        identity.setDateOfBirth(LocalDate.of(1995, 1, 1));
        identity.setDocumentExpiry(LocalDate.now().plusYears(10));
        identity.setStatus(PersonIdentityStatus.VERIFIED);
        identity.setVerificationMethod(IdentityVerificationMethod.AUTOMATED_POLICY);
        identity.setPolicyVersion("test-fixture");
        identity.setVerifiedAt(Instant.now());
        em.persist(identity);
        return identity;
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
