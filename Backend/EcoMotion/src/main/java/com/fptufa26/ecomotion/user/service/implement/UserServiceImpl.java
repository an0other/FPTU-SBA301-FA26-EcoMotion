package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.common.security.CustomUserDetailsService;
import com.fptufa26.ecomotion.user.dto.ProviderSummary;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.exception.CccdAlreadyExistsException;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.service.UserService.CccdIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final ProviderRepository providerRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    @Transactional(readOnly = true)
    public User loadForAuthentication(String email) {
        return findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long userId) {
        return userId == null ? Optional.empty() : userRepository.findById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ProviderSummary> findProvidersByIds(Collection<Long> providerIds) {
        if (providerIds.isEmpty()) {
            return Map.of();
        }
        return providerRepository.findAllById(providerIds).stream()
                .collect(Collectors.toMap(Provider::getId, provider -> new ProviderSummary(
                        provider.getId(),
                        provider.getDisplayName(),
                        provider.getProviderType(),
                        provider.getHotline(),
                        provider.getDepotAddress(),
                        provider.getDepotLatitude(),
                        provider.getDepotLongitude(),
                        provider.getRatingAvg(),
                        provider.getRatingCount())));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return email != null
                && userRepository.existsByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByPhone(String phone) {
        return phone != null && !phone.isBlank() && userRepository.existsByPhone(phone.trim());
    }

    @Override
    public User createUser(
            String email,
            String rawPassword,
            String fullName,
            String phone,
            Role role,
            UserStatus status) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists: " + normalizedEmail);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPhone(phone.trim());
        user.setFullName(fullName.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(status == null ? UserStatus.ACTIVE : status);
        return userRepository.save(user);
    }

    @Override
    public User createPendingUser(String email, String rawPassword, Role role) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists: " + normalizedEmail);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.PENDING);
        user.setEkycStatus(EkycStatus.UNVERIFIED);
        return userRepository.save(user);
    }

    @Override
    public User assignPhone(Long userId, String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        User user = loadUser(userId);
        String normalizedPhone = phone.trim();
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Phone already exists: " + normalizedPhone);
        }
        user.setPhone(normalizedPhone);
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Renter> findRenterProfile(Long userId) {
        return userId == null ? Optional.empty() : renterRepository.findByUser_Id(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Provider> findProviderProfile(Long userId) {
        return userId == null ? Optional.empty() : providerRepository.findByUser_Id(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Driver> findDriverProfile(Long userId) {
        return userId == null ? Optional.empty() : driverRepository.findByUser_Id(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Renter requireRenterByUserId(Long userId) {
        return renterRepository.findByUser_Id(userId)
                .orElseThrow(() -> new NotFoundException("Renter profile not found"));
    }

    @Override
    public User saveCccdIdentity(Long userId, CccdIdentity identity) {
        User user = loadUser(userId);
        Renter renter = loadOrCreateRenter(user);

        String cccdNo = identity.cccdNo() == null ? null : identity.cccdNo().trim();
        if (cccdNo != null
                && !cccdNo.isBlank()
                && !cccdNo.equals(renter.getCccdNo())
                && renterRepository.existsByCccdNo(cccdNo)) {
            throw new CccdAlreadyExistsException();
        }

        if (identity.fullName() != null && !identity.fullName().isBlank()) {
            user.setFullName(identity.fullName().trim());
        }
        user.setEkycStatus(EkycStatus.PENDING);
        userRepository.save(user);

        renter.setCccdNo(cccdNo);
        renter.setDateOfBirth(identity.dateOfBirth());
        renter.setGender(identity.gender());
        renter.setAddress(identity.address());
        renter.setCccdImageUrl(identity.cccdFrontUrl());
        renterRepository.save(renter);

        return user;
    }

    @Override
    public User activateWithPortrait(Long userId, String portraitUrl) {
        User user = loadUser(userId);

        Renter renter = loadOrCreateRenter(user);
        renter.setPortraitUrl(portraitUrl);
        renterRepository.save(renter);

        user.setEkycStatus(EkycStatus.VERIFIED);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private Renter loadOrCreateRenter(User user) {
        return renterRepository.findByUser_Id(user.getUserId()).orElseGet(() -> {
            Renter created = new Renter();
            created.setUser(user);
            return created;
        });
    }

    private User loadUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }

    @Override
    public Renter createRenterProfile(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User is required to create a renter profile");
        }
        Renter renter = new Renter();
        renter.setUser(user);
        return renterRepository.save(renter);
    }

    @Override
    public Provider createProviderProfile(
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
            String taxCode) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User is required to create a provider profile");
        }
        if (providerType == null) {
            throw new IllegalArgumentException("Provider type is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Provider display name is required");
        }
        if (hotline == null || hotline.isBlank()) {
            throw new IllegalArgumentException("Provider hotline is required");
        }
        if (depotAddress == null || depotAddress.isBlank()) {
            throw new IllegalArgumentException("Provider depot address is required");
        }

        Provider provider = new Provider();
        provider.setUser(user);
        provider.setProviderType(providerType);
        provider.setDisplayName(displayName.trim());
        provider.setHotline(hotline.trim());
        provider.setDepotAddress(depotAddress.trim());
        provider.setCommissionRate(commissionRate);
        provider.setDepositRatio(depositRatio);

        boolean anyBank = !isBlank(bankName) || !isBlank(bankAccountNo) || !isBlank(bankAccountHolder);
        if (anyBank) {
            String bCode = com.fptufa26.ecomotion.finance.enums.SupportedBank.fromName(bankName)
                    .map(com.fptufa26.ecomotion.finance.enums.SupportedBank::getCode)
                    .orElse("VCB");
            provider.setBankCode(bCode);
            provider.setBankName(bankName.trim());
            provider.setBankAccountNo(bankAccountNo.trim());
            provider.setBankAccountHolder(bankAccountHolder.trim());
        }

        if (providerType == ProviderType.BUSINESS) {
            provider.setBusinessLicenseNo(businessLicenseNo.trim());
            provider.setTaxCode(taxCode.trim());
        }

        return providerRepository.save(provider);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<User> listAdminUsers(Role role, UserStatus status, String keyword) {
        java.util.List<User> users;
        if (role != null) {
            users = userRepository.findByRoleOrderByCreatedAtDesc(role);
        } else if (status != null) {
            users = userRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            users = userRepository.findAllByOrderByCreatedAtDesc();
        }

        if (status != null && role != null) {
            users = users.stream().filter(u -> u.getStatus() == status).toList();
        }

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase(Locale.ROOT);
            users = users.stream()
                    .filter(u -> (u.getEmail() != null && u.getEmail().toLowerCase(Locale.ROOT).contains(lower))
                            || (u.getFullName() != null && u.getFullName().toLowerCase(Locale.ROOT).contains(lower))
                            || (u.getPhone() != null && u.getPhone().contains(lower)))
                    .toList();
        }

        return users;
    }

    @Override
    public User updateUserStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.fptufa26.ecomotion.common.exception.ResourceNotFoundException("Người dùng không tồn tại: " + userId));
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Override
    public User updateEkycStatus(Long userId, EkycStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.fptufa26.ecomotion.common.exception.ResourceNotFoundException("Người dùng không tồn tại: " + userId));
        user.setEkycStatus(status);
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<Provider> listProviders(ProviderStatus status) {
        if (status != null) {
            return providerRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return providerRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Provider updateProviderStatus(Long providerId, ProviderStatus status) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new com.fptufa26.ecomotion.common.exception.ResourceNotFoundException("Nhà cung cấp không tồn tại: " + providerId));
        provider.setStatus(status);
        if (status == ProviderStatus.APPROVED && provider.getUser() != null) {
            User user = provider.getUser();
            if (user.getStatus() == UserStatus.PENDING) {
                user.setStatus(UserStatus.ACTIVE);
                userRepository.save(user);
            }
        }
        return providerRepository.save(provider);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.fptufa26.ecomotion.user.entity.Driver> listDrivers(DriverStatus status) {
        if (status != null) {
            return driverRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return driverRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public com.fptufa26.ecomotion.user.entity.Driver updateDriverStatus(Long driverId, DriverStatus status, String rejectReason) {
        com.fptufa26.ecomotion.user.entity.Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new com.fptufa26.ecomotion.common.exception.ResourceNotFoundException("Tài xế không tồn tại: " + driverId));
        driver.setStatus(status);
        if (status == DriverStatus.REJECTED) {
            driver.setRejectReason(rejectReason != null ? rejectReason : "Hồ sơ không hợp lệ");
        } else {
            driver.setRejectReason(null);
            if (status == DriverStatus.APPROVED && driver.getUser() != null) {
                User user = driver.getUser();
                if (user.getStatus() == UserStatus.PENDING) {
                    user.setStatus(UserStatus.ACTIVE);
                    userRepository.save(user);
                }
            }
        }
        return driverRepository.save(driver);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUsersByStatus(UserStatus status) {
        return userRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUsersByRole(Role role) {
        return userRepository.countByRole(role);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingEkyc() {
        return userRepository.countByEkycStatus(EkycStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingProviders() {
        return providerRepository.countByStatus(ProviderStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingDrivers() {
        return driverRepository.countByStatus(DriverStatus.PENDING);
    }
}

