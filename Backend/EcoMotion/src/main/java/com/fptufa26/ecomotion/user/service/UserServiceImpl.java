package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.auth.exception.CccdAlreadyExistsException;
import com.fptufa26.ecomotion.user.entity.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.ProviderType;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final ProviderRepository providerRepository;
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
    public User saveCccdIdentity(Long userId, CccdIdentity identity) {
        User user = loadUser(userId);
        Renter renter = loadOrCreateRenter(user);

        String cccdNo = identity.cccdNo() == null ? null : identity.cccdNo().trim();
        // Re-scanning one's own CCCD is fine; claiming someone else's is not. The partial UNIQUE
        // index is still the real guard — this only turns the violation into a clear message.
        if (cccdNo != null
                && !cccdNo.isBlank()
                && !cccdNo.equals(renter.getCccdNo())
                && renterRepository.existsByCccdNo(cccdNo)) {
            throw new CccdAlreadyExistsException();
        }

        if (identity.fullName() != null && !identity.fullName().isBlank()) {
            user.setFullName(identity.fullName().trim());
        }
        // Onboarding has started but the identity is not verified yet.
        user.setEkycStatus(EkycStatus.PENDING);
        userRepository.save(user);

        renter.setCccdNo(cccdNo);
        renter.setDateOfBirth(identity.dateOfBirth());
        renter.setGender(identity.gender());
        renter.setAddress(identity.address());
        renter.setCccdImageUrl(identity.cccdFrontUrl());
        renter.setCccdImageBackUrl(identity.cccdBackUrl());
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

        // Bank trio is all-or-nothing (ck_provider_bank).
        boolean anyBank = !isBlank(bankName) || !isBlank(bankAccountNo) || !isBlank(bankAccountHolder);
        if (anyBank) {
            provider.setBankName(bankName.trim());
            provider.setBankAccountNo(bankAccountNo.trim());
            provider.setBankAccountHolder(bankAccountHolder.trim());
        }

        // Business fields are only allowed for BUSINESS providers (ck_provider_business_fields).
        if (providerType == ProviderType.BUSINESS) {
            provider.setBusinessLicenseNo(businessLicenseNo.trim());
            provider.setTaxCode(taxCode.trim());
        }

        return providerRepository.save(provider);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
