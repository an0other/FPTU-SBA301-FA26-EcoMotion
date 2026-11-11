package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.finance.enums.SupportedBank;
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
import com.fptufa26.ecomotion.user.exception.CccdAlreadyExistsException;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fptufa26.ecomotion.finance.enums.SupportedBank;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RenterRepository renterRepository;
    private final ProviderRepository providerRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final org.springframework.core.env.Environment environment;
    private final org.springframework.beans.factory.ObjectProvider<com.fptufa26.ecomotion.verification.identity.service.PersonIdentityQueryService> identityQueries;

    private boolean identityAvailableInCurrentProfile(com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity identity) {
        return identity.getVerificationMethod() != com.fptufa26.ecomotion.verification.identity.enums.IdentityVerificationMethod.DEMO_SEED
                || environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev & !prod"));
    }

    @Override
    public boolean canUsePhone(Long userId, String phone) {
        var owner = userId == null ? null : userRepository.findById(userId).orElse(null);
        return userRepository.findByPhone(phone).stream().allMatch(other -> userId != null && (other.getId().equals(userId)
                || owner != null && owner.getIdentity() != null && other.getIdentity() != null
                && owner.getIdentity().getId().equals(other.getIdentity().getId()) && owner.getRole() != other.getRole()
                && java.util.Set.of(Role.RENTER, Role.PROVIDER).contains(owner.getRole())
                && java.util.Set.of(Role.RENTER, Role.PROVIDER).contains(other.getRole())));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachLinkedIdentity(Long userId, Long identityId) {
        User user = loadUser(userId);
        var identity = identityQueries.getObject().findById(identityId).orElse(null);
        if (user.getStatus() != UserStatus.PENDING || user.getIdentity() != null || identity == null
                || identity.getVerificationMethod() == com.fptufa26.ecomotion.verification.identity.enums.IdentityVerificationMethod.DEMO_SEED
                || identity.getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED
                || userRepository.existsByIdentity_IdAndRole(identityId, user.getRole())) {
            throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                    org.springframework.http.HttpStatus.CONFLICT, "IDENTITY_LINK_NOT_ALLOWED", "Identity cannot be linked");
        }
        user.setIdentity(identity);
        user.setFullName(identity.getFullName());
        user.setEkycStatus(user.getRole() == Role.PROVIDER ? EkycStatus.PENDING : EkycStatus.VERIFIED);
        finishLinkedRenter(user);
        userRepository.saveAndFlush(user);
    }

    private void finishLinkedRenter(User user) {
        if (user.getRole() != Role.RENTER || user.getStatus() != UserStatus.PENDING
                || user.getIdentity() == null || user.getPhoneVerifiedAt() == null) return;
        if (user.getIdentity().getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED) {
            throw new IllegalStateException("Identity is not verified");
        }
        var renter = loadOrCreateRenter(user);
        renter.setCccdNo(user.getIdentity().getCccdNo());
        renter.setDateOfBirth(user.getIdentity().getDateOfBirth());
        renter.setGender(user.getIdentity().getGender());
        String address = user.getIdentity().getAddress();
        renter.setAddress(address == null || address.length() <= 255 ? address : address.substring(0, 255));
        renterRepository.save(renter);
        user.setStatus(UserStatus.ACTIVE);
        user.setEkycStatus(EkycStatus.VERIFIED);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markProviderIdentityReviewed(Long userId) {
        User user = lockForIdentityDecision(userId);
        if (user.getRole() != Role.PROVIDER || user.getStatus() != UserStatus.PENDING || user.getIdentity() == null
                || user.getPhoneVerifiedAt() == null
                || user.getIdentity().getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED) {
            throw new IllegalStateException("Provider identity review cannot be applied");
        }
        user.setIdentityReviewedAt(java.time.Instant.now());
        user.setEkycStatus(EkycStatus.VERIFIED);
        userRepository.saveAndFlush(user);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertRentalAllowed(Long renterUserId, Long providerUserId) {
        var renter = loadUser(renterUserId);
        var provider = loadUser(providerUserId);
        if (renter.getRole() != Role.RENTER || provider.getRole() != Role.PROVIDER
                || !renter.isActive() || !isProviderOperational(providerUserId)) {
            throw new org.springframework.security.access.AccessDeniedException("Account is not eligible for rental");
        }
        if (renter.getIdentity() == null || provider.getIdentity() == null
                || renter.getIdentity().getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED
                || provider.getIdentity().getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED) {
            throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                    org.springframework.http.HttpStatus.CONFLICT, "RENTAL_IDENTITY_REQUIRED", "Verified identities are required");
        }
        if (!identityAvailableInCurrentProfile(renter.getIdentity()) || !identityAvailableInCurrentProfile(provider.getIdentity())) {
            throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                    org.springframework.http.HttpStatus.CONFLICT, "RENTAL_SEED_ACCOUNT_UNAVAILABLE", "Seed accounts are only available in development");
        }
        if (identityExpired(renter.getIdentity()) || identityExpired(provider.getIdentity())) {
            throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                    org.springframework.http.HttpStatus.CONFLICT, "RENTAL_IDENTITY_EXPIRED", "Identity documents must be valid");
        }
        if (renter.getIdentity().getId().equals(provider.getIdentity().getId())) {
            throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                    org.springframework.http.HttpStatus.CONFLICT, "SELF_RENTAL_NOT_ALLOWED", "You cannot rent your own vehicle");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isProviderOperational(Long userId) {
        var user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() != Role.PROVIDER || !user.isActive() || user.getIdentity() == null
                || user.getIdentity().getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED
                || identityExpired(user.getIdentity()) || !identityAvailableInCurrentProfile(user.getIdentity())) return false;
        var application = entityManager.find(com.fptufa26.ecomotion.user.entity.ProviderApplication.class, userId);
        return application != null && application.getStatus() == com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus.APPROVED
                && providerRepository.findByUser_Id(userId).map(provider -> provider.getStatus() == ProviderStatus.APPROVED).orElse(false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public User lockForIdentityDecision(Long userId) {
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }

    private boolean identityExpired(com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity identity) {
        return identity.getDocumentExpiry() != null
                && identity.getDocumentExpiry().isBefore(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyVerifiedIdentity(Long userId, com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity identity) {
        User user = lockForIdentityDecision(userId);
        if (user.getIdentity() != null) {
            throw identityConflict("IDENTITY_ACCOUNT_ALREADY_LINKED", "Tài khoản đã có danh tính. Vui lòng tải lại hồ sơ trước khi duyệt.");
        }
        if (identity == null || identity.getId() == null
                || identity.getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED) {
            throw identityConflict("IDENTITY_VERIFIED_RECORD_REQUIRED", "Bản ghi danh tính chưa sẵn sàng. Vui lòng tải lại hồ sơ và thử lại.");
        }
        if (user.getStatus() != UserStatus.PENDING && user.getStatus() != UserStatus.ACTIVE) {
            throw identityConflict("IDENTITY_ACCOUNT_STATUS_INVALID", "Tài khoản đang bị khóa hoặc không còn đủ điều kiện nhận danh tính.");
        }
        if (user.getRole() != Role.RENTER && user.getRole() != Role.PROVIDER) {
            throw identityConflict("IDENTITY_ACCOUNT_ROLE_INVALID", "Vai trò tài khoản không được phép nhận danh tính.");
        }
        if (user.getPhone() == null || user.getPhone().isBlank() || user.getPhoneVerifiedAt() == null) {
            throw identityConflict("IDENTITY_PHONE_VERIFICATION_REQUIRED", "Tài khoản chưa hoàn tất xác nhận số điện thoại. Người đăng ký cần xác nhận OTP trước khi Admin duyệt danh tính.");
        }
        user.setIdentity(identity);
        if (user.getRole() == Role.PROVIDER) user.setIdentityReviewedAt(java.time.Instant.now());
        user.setFullName(identity.getFullName());
        user.setEkycStatus(EkycStatus.VERIFIED);
        if (user.getRole() == Role.RENTER) {
            Renter renter = loadOrCreateRenter(user);
            renter.setCccdNo(identity.getCccdNo());
            renter.setDateOfBirth(identity.getDateOfBirth());
            renter.setGender(identity.getGender());
            String address = identity.getAddress();
            renter.setAddress(address == null || address.length() <= 255 ? address : address.substring(0, 255));
            renterRepository.save(renter);
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.saveAndFlush(user);
    }

    private com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException identityConflict(String code, String message) {
        return new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                org.springframework.http.HttpStatus.CONFLICT, code, message);
    }

    @Override
    public Long resetPassword(String email, String rawPassword) {
        return applyNewPassword(email, rawPassword);
    }

    @Override
    public Long completeFirstLoginPasswordChange(String email, String rawPassword) {
        return applyNewPassword(email, rawPassword);
    }

    private Long applyNewPassword(String email, String rawPassword) {
        User user = findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(false);
        userRepository.saveAndFlush(user);
        return user.getUserId();
    }

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
        user.setEmailVerifiedAt(java.time.Instant.now());
        user.setTermsAcceptedAt(java.time.Instant.now());
        return userRepository.save(user);
    }

    @Override
    public User assignPhone(Long userId, String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        User user = lockForIdentityDecision(userId);
        if (user.getStatus() != UserStatus.PENDING && user.getStatus() != UserStatus.ACTIVE) {
            throw new org.springframework.security.access.AccessDeniedException("Account cannot verify a phone number");
        }
        String normalizedPhone = phone.trim();
        if (!canUsePhone(userId, normalizedPhone)) {
            throw new IllegalArgumentException("Phone already exists: " + normalizedPhone);
        }
        user.setPhone(normalizedPhone);
        user.setPhoneVerifiedAt(java.time.Instant.now());
        finishLinkedRenter(user);
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
            String bCode = SupportedBank.fromName(bankName)
                    .map(SupportedBank::getCode)
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
    public Optional<Provider> findProviderByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return providerRepository.findByUser_Id(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countDriversByProviderId(Long providerId) {
        if (providerId == null) {
            return 0;
        }
        return driverRepository.countByProvider_Id(providerId);
    }

    @Override
    public void updateDriverRating(
            Long driverId,
            BigDecimal ratingAvg,
            int ratingCount) {
        if (driverId == null) {
            throw new IllegalArgumentException("Driver id is required");
        }

        validateRatingSummary(ratingAvg, ratingCount);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Driver not found"));

        driver.setRatingAvg(ratingAvg);
        driver.setRatingCount(ratingCount);
    }

    @Override
    public void updateProviderRating(
            Long providerId,
            BigDecimal ratingAvg,
            int ratingCount) {
        if (providerId == null) {
            throw new IllegalArgumentException("Provider id is required");
        }

        validateRatingSummary(ratingAvg, ratingCount);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found"));

        provider.setRatingAvg(ratingAvg);
        provider.setRatingCount(ratingCount);
    }

    private void validateRatingSummary(
            BigDecimal ratingAvg,
            int ratingCount) {
        if (ratingAvg == null
                || ratingAvg.signum() < 0
                || ratingAvg.compareTo(BigDecimal.valueOf(5)) > 0
                || ratingCount < 0
                || (ratingCount == 0 && ratingAvg.signum() != 0)
                || (ratingCount > 0
                && ratingAvg.compareTo(BigDecimal.ONE) < 0)) {
            throw new IllegalArgumentException("Invalid rating summary");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockProviderForReview(Long providerId) {
        providerRepository.findProviderById(providerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> listAdminUsers(Role role, UserStatus status, String keyword) {
        List<User> users;
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
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + userId));
        if (user.getRole() == Role.PROVIDER && status == UserStatus.ACTIVE) {
            var provider = providerRepository.findByUser_Id(userId).orElse(null);
            var application = entityManager.find(com.fptufa26.ecomotion.user.entity.ProviderApplication.class, userId);
            if (provider == null || provider.getStatus() != ProviderStatus.APPROVED || user.getIdentity() == null
                    || user.getIdentity().getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED
                    || application == null || application.getStatus() != com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus.APPROVED) {
                throw new IllegalArgumentException("Provider onboarding approval is required");
            }
        }
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Override
    public User updateEkycStatus(Long userId, EkycStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + userId));
        if (status == EkycStatus.VERIFIED) {
            Renter renter = renterRepository.findByUser_Id(userId).orElse(null);
            if (renter == null
                    || isBlank(renter.getCccdNo())
                    || renter.getDateOfBirth() == null
                    || isBlank(renter.getGender())
                    || isBlank(renter.getAddress())
                    || isBlank(renter.getCccdImageUrl())
                    || isBlank(renter.getPortraitUrl())) {
                throw new IllegalArgumentException(
                        "Không thể xác nhận eKYC hợp lệ: Hồ sơ đang thiếu thông tin định danh bắt buộc (Số CCCD, ngày sinh, giới tính, địa chỉ) hoặc thiếu ảnh chụp CCCD/chân dung đối chiếu.");
            }
        }
        user.setEkycStatus(status);
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Provider> listProviders(ProviderStatus status) {
        if (status != null) {
            return providerRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return providerRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Provider updateProviderStatus(Long providerId, ProviderStatus status) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Nhà cung cấp không tồn tại: " + providerId));
        if (status == ProviderStatus.APPROVED && provider.getStatus() != ProviderStatus.APPROVED) {
            var application = entityManager.find(com.fptufa26.ecomotion.user.entity.ProviderApplication.class, provider.getUser().getId());
            if (provider.getStatus() != ProviderStatus.LOCKED || application == null
                    || application.getStatus() != com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus.APPROVED
                    || provider.getUser().getIdentity() == null
                    || provider.getUser().getIdentity().getStatus() != com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED) {
                throw new IllegalArgumentException("Use provider application review to approve onboarding");
            }
        }
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
    @Transactional
    public Provider updateProviderBankSnapshot(Long providerId, String bankCode, String bankName,
                                               String accountNumber, String accountHolder) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new NotFoundException(
                        "Không tìm thấy nhà cung cấp"));
        provider.setBankCode(bankCode);
        provider.setBankName(bankName);
        provider.setBankAccountNo(accountNumber);
        provider.setBankAccountHolder(accountHolder);
        return providerRepository.save(provider);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Driver> listDrivers(DriverStatus status) {
        if (status != null) {
            return driverRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return driverRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Driver updateDriverStatus(Long driverId, DriverStatus status, String rejectReason) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài xế không tồn tại: " + driverId));
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
        return userRepository.countByRoleAndEkycStatus(Role.RENTER, EkycStatus.PENDING);
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

    @Override
    @Transactional(readOnly = true)
    public Optional<Provider> findProviderById(Long providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return providerRepository.findById(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Provider> findAllProviders() {
        return providerRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Driver> findDriverById(Long driverId) {
        if (driverId == null) {
            return Optional.empty();
        }
        return driverRepository.findById(driverId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Driver> findDriversByProviderId(Long providerId) {
        if (providerId == null) {
            return List.of();
        }
        return driverRepository.findByProvider_IdWithUser(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Driver> findDriversByProviderIdAndStatus(Long providerId, DriverStatus status) {
        if (providerId == null) {
            return List.of();
        }
        return driverRepository.findByProvider_IdAndStatus(providerId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Driver> findDriverByIdAndProviderId(Long driverId, Long providerId) {
        if (driverId == null || providerId == null) {
            return Optional.empty();
        }
        return driverRepository.findByIdAndProvider_Id(driverId, providerId);
    }

    @Override
    @Transactional
    public Optional<Driver> findDriverByIdForUpdate(Long driverId) {
        if (driverId == null) {
            return Optional.empty();
        }
        Optional<Driver> result = driverRepository.findByIdForUpdate(driverId);
        // Candidate evaluation may already have loaded this driver before waiting for the lock.
        result.ifPresent(driver -> entityManager.refresh(driver, LockModeType.PESSIMISTIC_WRITE));
        return result;
    }

    @Override
    @Transactional
    public Driver saveDriver(Driver driver) {
        return driverRepository.save(driver);
    }

    @Override
    @Transactional
    public Provider saveProvider(Provider provider) {
        return providerRepository.save(provider);
    }
}
