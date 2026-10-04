package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.LoginRequest;
import com.fptufa26.ecomotion.auth.dto.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.RefreshResponse;
import com.fptufa26.ecomotion.auth.entity.RefreshToken;
import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.exception.InvalidRefreshTokenException;
import com.fptufa26.ecomotion.auth.mapper.AuthMapper;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.User;
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
    private final Duration ttlRememberMe;
    private final Duration ttlDefault;

    public AuthServiceImpl(
            UserService userService,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthMapper authMapper,
            @Value("${app.refresh-token.ttl-remember-me:7d}") Duration ttlRememberMe,
            @Value("${app.refresh-token.ttl-default:1d}") Duration ttlDefault) {
        this.userService = userService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authMapper = authMapper;
        this.ttlRememberMe = ttlRememberMe;
        this.ttlDefault = ttlDefault;
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userService.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new AccountInactiveException();
        }

        String accessToken = jwtService.generateAccessToken(user);

        String rawRefreshToken = generateOpaqueToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256Hex(rawRefreshToken));
        entity.setExpiresAt(Instant.now().plus(request.rememberMe() ? ttlRememberMe : ttlDefault));
        entity.setUserAgent(truncate(userAgent, USER_AGENT_MAX));
        entity.setIpAddress(truncate(ipAddress, IP_ADDRESS_MAX));
        refreshTokenRepository.save(entity);

        LoginResponse response = authMapper.toLoginResponse(
                user, accessToken, jwtService.getAccessTokenTtlSeconds());
        return new LoginResult(response, rawRefreshToken, request.rememberMe());
    }

    @Override
    @Transactional
    public RefreshResponse refresh(String refreshToken) {

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
