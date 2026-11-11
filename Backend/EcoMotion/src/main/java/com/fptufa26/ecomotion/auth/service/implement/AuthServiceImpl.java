package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.captcha.CaptchaVerifier;
import com.fptufa26.ecomotion.auth.dto.request.LoginRequest;
import com.fptufa26.ecomotion.auth.dto.response.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.response.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.response.RefreshResponse;
import com.fptufa26.ecomotion.auth.entity.RefreshToken;
import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.AccountPendingException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.exception.InvalidRefreshTokenException;
import com.fptufa26.ecomotion.auth.exception.PasswordChangeRequiredException;
import com.fptufa26.ecomotion.auth.mapper.AuthMapper;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.auth.service.AuthService;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final int USER_AGENT_MAX = 255;
    private static final int IP_ADDRESS_MAX = 45;

    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMapper authMapper;
    private final CaptchaVerifier captchaVerifier;
    private final Duration ttlRememberMe;
    private final Duration ttlDefault;
    private final com.fptufa26.ecomotion.user.service.ProviderOnboardingService onboarding;

    public AuthServiceImpl(
            UserService userService,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthMapper authMapper,
            CaptchaVerifier captchaVerifier,
            @Value("${app.refresh-token.ttl-remember-me:7d}") Duration ttlRememberMe,
            @Value("${app.refresh-token.ttl-default:1d}") Duration ttlDefault,
            com.fptufa26.ecomotion.user.service.ProviderOnboardingService onboarding) {
        this.userService = userService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authMapper = authMapper;
        this.captchaVerifier = captchaVerifier;
        this.ttlRememberMe = ttlRememberMe;
        this.ttlDefault = ttlDefault;
        this.onboarding = onboarding;
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        captchaVerifier.verify(request.captchaToken(), "login", ipAddress);

        boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
        return authenticateAndIssue(request.email(), request.password(), rememberMe, ipAddress, userAgent);
    }

    @Override
    @Transactional
    public LoginResult issueSessionFor(String email, String rawPassword, String ipAddress, String userAgent) {
        return authenticateAndIssue(email, rawPassword, false, ipAddress, userAgent);
    }

    private LoginResult authenticateAndIssue(String email, String rawPassword, boolean rememberMe,
                                            String ipAddress, String userAgent) {
        User user = userService.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!canLogin(user)) {
            if (user.getStatus() == UserStatus.PENDING) {
                throw new AccountPendingException();
            }
            throw new AccountInactiveException();
        }

        if (user.isMustChangePassword()) {
            throw new PasswordChangeRequiredException();
        }

        String sessionScope = sessionScope(user);
        String accessToken = jwtService.generateAccessToken(user, sessionScope);

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256Hex(rawRefreshToken));
        entity.setFamilyId(UUID.randomUUID().toString());
        entity.setRememberMe(rememberMe);
        entity.setExpiresAt(Instant.now().plus(rememberMe ? ttlRememberMe : ttlDefault));
        entity.setUserAgent(truncate(userAgent, USER_AGENT_MAX));
        entity.setIpAddress(truncate(ipAddress, IP_ADDRESS_MAX));
        refreshTokenRepository.save(entity);

        LoginResponse response = authMapper.toLoginResponse(
                user, accessToken, jwtService.getAccessTokenTtlSeconds(), sessionScope);
        return new LoginResult(response, rawRefreshToken, rememberMe);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshResult refresh(String refreshToken) {
        return refreshInternal(refreshToken);
    }

    private RefreshResult refreshInternal(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(sha256Hex(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();
        if (stored.isRevoked()) {
            if (stored.getFamilyId() != null && now.isBefore(stored.getExpiresAt())) {
                refreshTokenRepository.revokeActiveByFamilyId(stored.getFamilyId(), now);
            }
            throw new InvalidRefreshTokenException();
        }
        if (!now.isBefore(stored.getExpiresAt())) {
            throw new InvalidRefreshTokenException();
        }

        User user = stored.getUser();
        if (!canLogin(user)) {
            throw new AccountInactiveException();
        }
        if (user.isMustChangePassword()) {
            throw new InvalidRefreshTokenException();
        }

        String familyId = stored.getFamilyId() == null
                ? UUID.randomUUID().toString() : stored.getFamilyId();
        boolean rememberMe = stored.getRememberMe() != null
                ? stored.getRememberMe()
                : stored.getCreatedAt() != null
                    && Duration.between(stored.getCreatedAt(), stored.getExpiresAt())
                        .compareTo(ttlDefault.plus(ttlRememberMe).dividedBy(2)) > 0;
        stored.setFamilyId(familyId);
        stored.setRevokedAt(now);

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken replacement = new RefreshToken();
        replacement.setUser(user);
        replacement.setTokenHash(sha256Hex(rawRefreshToken));
        replacement.setFamilyId(familyId);
        replacement.setRememberMe(rememberMe);
        replacement.setExpiresAt(stored.getExpiresAt());
        replacement.setUserAgent(stored.getUserAgent());
        replacement.setIpAddress(stored.getIpAddress());
        refreshTokenRepository.save(replacement);

        String sessionScope = sessionScope(user);
        String accessToken = jwtService.generateAccessToken(user, sessionScope);
        RefreshResponse response = authMapper.toRefreshResponse(accessToken, jwtService.getAccessTokenTtlSeconds(), sessionScope);
        return new RefreshResult(response, rawRefreshToken, rememberMe, stored.getExpiresAt());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken)).ifPresent(stored -> {
            if (!stored.isRevoked()) {
                stored.setRevokedAt(Instant.now());
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: id=" + userId));
        String identity = user.getIdentity() == null ? "UNVERIFIED" : user.getIdentity().getStatus().name();
        if (user.getRole() == com.fptufa26.ecomotion.user.enums.Role.PROVIDER) {
            var application = onboarding.get(userId);
            var provider = userService.findProviderProfile(userId).orElse(null);
            boolean full = userService.isProviderOperational(userId);
            String next = user.getPhoneVerifiedAt() == null ? "VERIFY_PHONE" : application.nextAction();
            if (provider != null && provider.getStatus() == com.fptufa26.ecomotion.user.enums.ProviderStatus.LOCKED) {
                next = "CONTACT_SUPPORT";
            }
            return authMapper.toCurrentUserResponse(user).withAccess(full ? "FULL" : "ONBOARDING", identity,
                    provider == null ? application.status().name() : provider.getStatus().name(),
                    full ? "PROVIDER_DASHBOARD" : next,
                    full ? java.util.List.of("PROVIDER_OPERATIONS", "VIEW_ONBOARDING")
                            : application.canEdit() ? java.util.List.of("VIEW_ONBOARDING", "VERIFY_IDENTITY", "EDIT_PROVIDER_APPLICATION")
                            : java.util.List.of("VIEW_ONBOARDING"));
        }
        return authMapper.toCurrentUserResponse(user).withAccess("FULL", identity, null, "DASHBOARD",
                java.util.List.of("ROLE_" + user.getRole().name()));
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean canLogin(User user) {
        return user.isActive() || (user.getRole() == com.fptufa26.ecomotion.user.enums.Role.PROVIDER
                && user.getStatus() == UserStatus.PENDING && user.getEmailVerifiedAt() != null);
    }

    private String sessionScope(User user) {
        return user.getRole() == com.fptufa26.ecomotion.user.enums.Role.PROVIDER
                && !userService.isProviderOperational(user.getId()) ? "ONBOARDING" : "FULL";
    }

    static String sha256Hex(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
