package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.ProviderSummary;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserService {

    Optional<User> findByEmail(String email);

    User loadForAuthentication(String email);

    Optional<User> findById(Long userId);

    Map<Long, ProviderSummary> findProvidersByIds(Collection<Long> providerIds);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    User createUser(String email, String rawPassword, String fullName, String phone, Role role, UserStatus status);

    Renter requireRenterByUserId(Long userId);

    User createPendingUser(String email, String rawPassword, Role role);

    User assignPhone(Long userId, String phone);

    Optional<Renter> findRenterProfile(Long userId);

    Optional<Provider> findProviderProfile(Long userId);

    Optional<Provider> findProviderByUserId(Long userId);

    Optional<Driver> findDriverProfile(Long userId);

    long countDriversByProviderId(Long providerId);

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

    void updateDriverRating(Long driverId, BigDecimal ratingAvg, int ratingCount);

    void updateProviderRating(Long providerId, BigDecimal ratingAvg, int ratingCount);

    void lockProviderForReview(Long providerId);

    List<User> listAdminUsers(Role role, UserStatus status, String keyword);

    User updateUserStatus(Long userId, UserStatus status);

    User updateEkycStatus(Long userId, EkycStatus status);

    List<Provider> listProviders(ProviderStatus status);

    Provider updateProviderStatus(Long providerId, ProviderStatus status);

    Provider updateProviderBankSnapshot(Long providerId, String bankCode, String bankName,
                                        String accountNumber, String accountHolder);

    List<Driver> listDrivers(DriverStatus status);

    Driver updateDriverStatus(Long driverId, DriverStatus status, String rejectReason);

    long countUsersByStatus(UserStatus status);

    long countUsersByRole(Role role);

    long countPendingEkyc();

    long countPendingProviders();

    long countPendingDrivers();

    Optional<Provider> findProviderById(Long providerId);

    List<Provider> findAllProviders();

    Optional<Driver> findDriverById(Long driverId);

    List<Driver> findDriversByProviderId(Long providerId);

    List<Driver> findDriversByProviderIdAndStatus(Long providerId, DriverStatus status);

    Optional<Driver> findDriverByIdAndProviderId(Long driverId, Long providerId);

    Optional<Driver> findDriverByIdForUpdate(Long driverId);

    Driver saveDriver(Driver driver);

    Provider saveProvider(Provider provider);
}
