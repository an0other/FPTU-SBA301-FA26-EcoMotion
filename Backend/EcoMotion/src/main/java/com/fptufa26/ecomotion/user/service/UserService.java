package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.ProviderType;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface UserService {

    Optional<User> findByEmail(String email);

    User loadForAuthentication(String email);

    Optional<User> findById(Long userId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    User createUser(String email, String rawPassword, String fullName, String phone, Role role, UserStatus status);

    /**
     * Creates a self-registered account with only an email, a password and a role. Phone and full
     * name stay null: they are captured on the activation page. The account is always PENDING.
     */
    User createPendingUser(String email, String rawPassword, Role role);

    /** Sets the phone number captured (and OTP-verified) during activation. */
    User assignPhone(Long userId, String phone);

    /** The renter profile of an account, used by the activation steps to read persisted progress. */
    Optional<Renter> findRenterProfile(Long userId);

    /**
     * Persists step 2 of activation: writes the identity fields read from the CCCD (plus the two
     * image paths) onto the renter profile and sets the account's full name. Called as soon as the
     * scan succeeds, so progress survives a reload or a backend restart.
     */
    User saveCccdIdentity(Long userId, CccdIdentity identity);

    /**
     * Persists step 3 of activation — the last one. Stores the selfie and flips the account to
     * ACTIVE with {@code ekyc_status = VERIFIED}. Only ever called after the face matched.
     */
    User activateWithPortrait(Long userId, String portraitUrl);

    Renter createRenterProfile(User user);

    /** Identity data read from the CCCD front image during activation. */
    record CccdIdentity(
            String fullName,
            String cccdNo,
            LocalDate dateOfBirth,
            String gender,
            String address,
            String cccdFrontUrl,
            String cccdBackUrl
    ) {
    }

    Provider createProviderProfile(
            User user,
            ProviderType providerType,
            String displayName,
            String hotline,
            String depotAddress,
            BigDecimal commissionRate,
            BigDecimal depositRatio,
            String bankName,
            String bankAccountNo,
            String bankAccountHolder,
            String businessLicenseNo,
            String taxCode);
}
