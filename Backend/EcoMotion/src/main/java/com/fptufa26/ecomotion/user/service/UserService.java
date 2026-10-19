package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
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

    User createPendingUser(String email, String rawPassword, Role role);

    User assignPhone(Long userId, String phone);

    Optional<Renter> findRenterProfile(Long userId);

    Optional<Provider> findProviderProfile(Long userId);

    Optional<Driver> findDriverProfile(Long userId);

    User saveCccdIdentity(Long userId, CccdIdentity identity);

    User activateWithPortrait(Long userId, String portraitUrl);

    Renter createRenterProfile(User user);

    record CccdIdentity(
            String fullName,
            String cccdNo,
            LocalDate dateOfBirth,
            String gender,
            String address,
            String cccdFrontUrl
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
