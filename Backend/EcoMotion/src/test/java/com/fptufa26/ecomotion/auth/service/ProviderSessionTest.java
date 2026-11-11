package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.captcha.CaptchaVerifier;
import com.fptufa26.ecomotion.auth.exception.*;
import com.fptufa26.ecomotion.auth.mapper.AuthMapper;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.auth.service.implement.AuthServiceImpl;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProviderSessionTest {
    private final UserService users = mock(UserService.class);
    private final RefreshTokenRepository refresh = mock(RefreshTokenRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder passwords = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
    private final JwtService jwt = new JwtService("test-secret-with-more-than-thirty-two-bytes", Duration.ofMinutes(15), "test");
    private AuthServiceImpl service;
    private User user;

    @BeforeEach
    void setup() {
        service = new AuthServiceImpl(users, refresh, passwords, jwt, mock(AuthMapper.class), mock(CaptchaVerifier.class),
                Duration.ofDays(7), Duration.ofDays(1), mock(ProviderOnboardingService.class));
        user = new User();
        user.setId(1L);
        user.setRole(Role.PROVIDER);
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerifiedAt(Instant.now());
        user.setPasswordHash("hash");
        when(users.findByEmail("email")).thenReturn(Optional.of(user));
        when(passwords.matches("password", "hash")).thenReturn(true);
    }

    @Test
    void pendingProviderGetsSessionAndOnboardingToken() {
        assertNotNull(service.issueSessionFor("email", "password", null, null));
        verify(refresh).save(any());
        var claims = jwt.parseClaims(jwt.generateAccessToken(user));
        assertEquals("ONBOARDING", claims.get("scope", String.class));
        assertEquals("ACCESS", claims.get("purpose", String.class));
    }

    @Test
    void pendingRenterStillGetsActivationRequired() {
        user.setRole(Role.RENTER);
        assertThrows(AccountPendingException.class, () -> service.issueSessionFor("email", "password", null, null));
        verifyNoInteractions(refresh);
    }

    @Test
    void lockedOrUnverifiedProviderCannotGetSession() {
        user.setStatus(UserStatus.LOCKED);
        assertThrows(AccountInactiveException.class, () -> service.issueSessionFor("email", "password", null, null));
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerifiedAt(null);
        assertThrows(AccountPendingException.class, () -> service.issueSessionFor("email", "password", null, null));
    }

    @Test
    void pendingProviderCanRotateRefreshToken() {
        var stored = new com.fptufa26.ecomotion.auth.entity.RefreshToken();
        stored.setUser(user);
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        stored.setFamilyId("family");
        stored.setRememberMe(false);
        when(refresh.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));
        assertNotNull(service.refresh("opaque-token"));
        assertNotNull(stored.getRevokedAt());
        verify(refresh).save(argThat(replacement -> "family".equals(replacement.getFamilyId())
                && replacement.getExpiresAt().equals(stored.getExpiresAt())));
    }

    @Test
    void lockedProviderCannotRefresh() {
        user.setStatus(UserStatus.LOCKED);
        var stored = new com.fptufa26.ecomotion.auth.entity.RefreshToken();
        stored.setUser(user);
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refresh.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(stored));
        assertThrows(AccountInactiveException.class, () -> service.refresh("opaque-token"));
        verify(refresh, never()).save(any());
    }

    @Test
    void activationTokenCannotBeUsedAsAccessToken() {
        var tokens = new com.fptufa26.ecomotion.auth.service.implement.TokenServiceImpl(
                "test-secret-with-more-than-thirty-two-bytes", Duration.ofMinutes(30), Duration.ofMinutes(60), "test");
        assertFalse(jwt.isTokenValid(tokens.issueActivation(1L, "email", Role.PROVIDER)));
        assertTrue(jwt.isTokenValid(jwt.generateAccessToken(user)));
    }
}
