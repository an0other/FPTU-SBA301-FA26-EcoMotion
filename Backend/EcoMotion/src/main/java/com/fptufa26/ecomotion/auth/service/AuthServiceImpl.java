package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.LoginRequest;
import com.fptufa26.ecomotion.auth.dto.response.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.response.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.response.RefreshResponse;
import com.fptufa26.ecomotion.auth.entity.RefreshToken;
import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.AccountPendingException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.exception.InvalidRefreshTokenException;
import com.fptufa26.ecomotion.auth.mapper.AuthMapper;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
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
    private final CaptchaService captchaService;
    private final Duration ttlRememberMe;
    private final Duration ttlDefault;

    public AuthServiceImpl(
            UserService userService,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthMapper authMapper,
            CaptchaService captchaService,
            @Value("${app.refresh-token.ttl-remember-me:7d}") Duration ttlRememberMe,
            @Value("${app.refresh-token.ttl-default:1d}") Duration ttlDefault) {
        this.userService = userService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authMapper = authMapper;
        this.captchaService = captchaService;
        this.ttlRememberMe = ttlRememberMe;
        this.ttlDefault = ttlDefault;
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        // The client cannot be trusted to have solved the challenge: verify server-side first.
        captchaService.verify(request.captchaToken(), "login", ipAddress);

        boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
        return authenticateAndIssue(request.email(), request.password(), rememberMe, ipAddress, userAgent);
    }

    @Override
    @Transactional
    public LoginResult issueSessionFor(String email, String rawPassword, String ipAddress, String userAgent) {
        return authenticateAndIssue(email, rawPassword, false, ipAddress, userAgent);
    }

    /**
     * Shared credential check + session issuing. Deliberately does NOT verify captcha: callers are
     * responsible for that (see {@link #login}) or for having already established the identity
     * server-side (see {@link #issueSessionFor}).
     */
    private LoginResult authenticateAndIssue(String email, String rawPassword, boolean rememberMe,
                                            String ipAddress, String userAgent) {
        User user = userService.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            // PENDING accounts can still be activated — point the client at the activation page.
            if (user.getStatus() == UserStatus.PENDING) {
                throw new AccountPendingException();
            }
            throw new AccountInactiveException();
        }

        String accessToken = jwtService.generateAccessToken(user);

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256Hex(rawRefreshToken));
        entity.setExpiresAt(Instant.now().plus(rememberMe ? ttlRememberMe : ttlDefault));
        entity.setUserAgent(truncate(userAgent, USER_AGENT_MAX));
        entity.setIpAddress(truncate(ipAddress, IP_ADDRESS_MAX));
        refreshTokenRepository.save(entity);

        LoginResponse response = authMapper.toLoginResponse(
                user, accessToken, jwtService.getAccessTokenTtlSeconds());
        return new LoginResult(response, rawRefreshToken, rememberMe);
    }

    @Override
    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        // TODO (chưa làm — ghi lại 2026-10-06): KHÔNG rotate refresh token, và KHÔNG revoke cascade.
        //
        // Hiện trạng: trả access token mới nhưng giữ nguyên refresh token cũ (chỉ đọc, không đổi).
        //
        // Hệ quả:
        //  - Refresh token bị đánh cắp thì dùng được suốt 1d/7d; vì không rotate nên không có cách
        //    nào phát hiện việc dùng lại (reuse detection) — nạn nhân và kẻ tấn công dùng song song
        //    mà hệ thống không thấy gì.
        //  - logout() chỉ revoke ĐÚNG refresh token hiện tại, không revoke toàn bộ phiên của user
        //    (ví dụ user đăng nhập ở nhiều máy thì logout ở 1 máy không thu hồi được các máy khác).
        //
        // Cách sửa dự kiến:
        //  1. Rotate: sinh refresh token mới mỗi lần refresh, set revoked_at cho token cũ, trả cookie mới
        //     (controller phải set lại Set-Cookie trong /auth/refresh).
        //  2. Reuse detection: nếu nhận được một token ĐÃ revoked mà chưa hết hạn → coi như bị lộ,
        //     revoke toàn bộ refresh token của user đó và buộc đăng nhập lại.
        //  3. Thêm thao tác revoke-all theo userId cho logout.
        return refreshInternal(refreshToken);
    }

    private RefreshResponse refreshInternal(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.isRevoked() || stored.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw new AccountInactiveException();
        }

        String accessToken = jwtService.generateAccessToken(user);
        return authMapper.toRefreshResponse(accessToken, jwtService.getAccessTokenTtlSeconds());
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
        return authMapper.toCurrentUserResponse(user);
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
