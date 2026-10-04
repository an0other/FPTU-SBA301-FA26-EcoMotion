package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.LoginRequest;
import com.fptufa26.ecomotion.auth.entity.RefreshToken;
import com.fptufa26.ecomotion.auth.exception.AccountInactiveException;
import com.fptufa26.ecomotion.auth.exception.InvalidCredentialsException;
import com.fptufa26.ecomotion.auth.exception.InvalidRefreshTokenException;
import com.fptufa26.ecomotion.auth.mapper.AuthMapper;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for {@link AuthServiceImpl} with mocked repositories (plan §11 Phase 5). */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "renter01@ecomotion.vn";
    private static final String PASSWORD = "P@ssw0rd";
    private static final String RAW_REFRESH_TOKEN = "raw-opaque-refresh-token";
    private static final Duration TTL_REMEMBER_ME = Duration.ofDays(7);
    private static final Duration TTL_DEFAULT = Duration.ofDays(1);
    private static final String SECRET = "test-secret-key-for-unit-and-integration-tests-32bytes";

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(15), "ecomotion");
        authService = new AuthServiceImpl(
                userService,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                new AuthMapper(),
                TTL_REMEMBER_ME,
                TTL_DEFAULT);
    }

    private User activeUser() {
        User user = new User();
        user.setUserId(42L);
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(Role.RENTER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RefreshToken storedToken(User user, Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(AuthServiceImpl.sha256Hex(RAW_REFRESH_TOKEN));
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        token.setCreatedAt(Instant.now().minus(Duration.ofMinutes(1)));
        return token;
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("login with rememberMe=true persists a 7-day refresh token")
    void loginWithRememberMePersistsSevenDayToken() {
        User user = activeUser();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthService.LoginResult result =
                authService.login(new LoginRequest(EMAIL, PASSWORD, true), "127.0.0.1", "JUnit");

        RefreshToken saved = captureSavedToken();
        assertThat(saved.getExpiresAt())
                .isBetween(Instant.now().plus(TTL_REMEMBER_ME).minusSeconds(10),
                        Instant.now().plus(TTL_REMEMBER_ME).plusSeconds(10));
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(result.refreshToken());
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("JUnit");

        assertThat(result.rememberMe()).isTrue();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.response().expiresIn()).isEqualTo(900);
        assertThat(result.response().user().role()).isEqualTo(Role.RENTER);
    }

    @Test
    @DisplayName("login with rememberMe=false persists a 1-day refresh token")
    void loginWithoutRememberMePersistsOneDayToken() {
        User user = activeUser();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthService.LoginResult result =
                authService.login(new LoginRequest(EMAIL, PASSWORD, false), "127.0.0.1", null);

        RefreshToken saved = captureSavedToken();
        assertThat(saved.getExpiresAt())
                .isBetween(Instant.now().plus(TTL_DEFAULT).minusSeconds(10),
                        Instant.now().plus(TTL_DEFAULT).plusSeconds(10));
        assertThat(result.rememberMe()).isFalse();
    }

    @Test
    @DisplayName("login resolves the account by email and echoes it back in the response")
    void loginResolvesUserByEmail() {
        User user = activeUser();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthService.LoginResult result =
                authService.login(new LoginRequest(EMAIL, PASSWORD, false), "127.0.0.1", null);

        assertThat(result.response().user().email()).isEqualTo(EMAIL);
        verify(userService).findByEmail(EMAIL);
    }

    @Test
    @DisplayName("login with rememberMe omitted (null) is normalised to false")
    void loginWithOmittedRememberMeDefaultsToFalse() {
        User user = activeUser();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthService.LoginResult result =
                authService.login(new LoginRequest(EMAIL, PASSWORD, null), "127.0.0.1", null);

        assertThat(result.rememberMe()).isFalse();
        RefreshToken saved = captureSavedToken();
        assertThat(saved.getExpiresAt())
                .isBetween(Instant.now().plus(TTL_DEFAULT).minusSeconds(10),
                        Instant.now().plus(TTL_DEFAULT).plusSeconds(10));
    }

    @Test
    @DisplayName("wrong password throws the generic 401 message")
    void loginWithWrongPasswordThrowsInvalidCredentials() {
        User user = activeUser();
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(EMAIL, "not-the-password", false), "127.0.0.1", null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.MESSAGE);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("unknown email throws exactly the same message as a wrong password")
    void loginWithUnknownEmailThrowsSameGenericMessage() {
        when(userService.findByEmail("ghost@ecomotion.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("ghost@ecomotion.vn", PASSWORD, false), "127.0.0.1", null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(InvalidCredentialsException.MESSAGE);
    }

    @Test
    @DisplayName("inactive account throws 403")
    void loginWithInactiveAccountThrowsAccountInactive() {
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);
        when(userService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest(EMAIL, PASSWORD, false), "127.0.0.1", null))
                .isInstanceOf(AccountInactiveException.class)
                .hasMessage(AccountInactiveException.MESSAGE);

        verifyNoInteractions(refreshTokenRepository);
    }

    // ---------------------------------------------------------------- refresh

    @Test
    @DisplayName("refresh with a valid token issues a new access token")
    void refreshWithValidTokenReturnsNewAccessToken() {
        User user = activeUser();
        RefreshToken stored = storedToken(user, Instant.now().plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(stored));

        var response = authService.refresh(RAW_REFRESH_TOKEN);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    @DisplayName("refresh with a revoked token is rejected")
    void refreshWithRevokedTokenThrows() {
        User user = activeUser();
        RefreshToken stored = storedToken(user, Instant.now().plus(Duration.ofDays(1)), Instant.now());
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("refresh with an expired token is rejected")
    void refreshWithExpiredTokenThrows() {
        User user = activeUser();
        RefreshToken stored = storedToken(user, Instant.now().minus(Duration.ofMinutes(1)), null);
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("refresh with an unknown token is rejected")
    void refreshWithUnknownTokenThrows() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("refresh without a cookie is rejected")
    void refreshWithoutTokenThrows() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> authService.refresh("  "))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("refresh for an account deactivated after issuance is rejected")
    void refreshWithInactiveUserThrowsAccountInactive() {
        User user = activeUser();
        user.setStatus(UserStatus.LOCKED);
        RefreshToken stored = storedToken(user, Instant.now().plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(RAW_REFRESH_TOKEN))
                .isInstanceOf(AccountInactiveException.class);
    }

    // ----------------------------------------------------------------- logout

    @Test
    @DisplayName("logout stamps revoked_at on the stored token")
    void logoutRevokesToken() {
        User user = activeUser();
        RefreshToken stored = storedToken(user, Instant.now().plus(Duration.ofDays(1)), null);
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(stored));

        authService.logout(RAW_REFRESH_TOKEN);

        assertThat(stored.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("logout without a cookie is a silent no-op")
    void logoutWithoutCookieIsIdempotent() {
        authService.logout(null);
        authService.logout("   ");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("logout of an already revoked token does not overwrite the timestamp")
    void logoutDoesNotOverwriteExistingRevocation() {
        User user = activeUser();
        Instant original = Instant.now().minus(Duration.ofHours(3));
        RefreshToken stored = storedToken(user, Instant.now().plus(Duration.ofDays(1)), original);
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.sha256Hex(RAW_REFRESH_TOKEN)))
                .thenReturn(Optional.of(stored));

        authService.logout(RAW_REFRESH_TOKEN);

        assertThat(stored.getRevokedAt()).isEqualTo(original);
        verify(refreshTokenRepository, never()).save(any());
    }

    // ----------------------------------------------------------- current user

    @Test
    @DisplayName("current user is mapped from the persisted row")
    void getCurrentUserReturnsMappedUser() {
        User user = activeUser();
        when(userService.findById(42L)).thenReturn(Optional.of(user));

        var response = authService.getCurrentUser(42L);

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.role()).isEqualTo(Role.RENTER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("current user of a deleted account is rejected")
    void getCurrentUserWithUnknownIdThrows() {
        when(userService.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(999L))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private RefreshToken captureSavedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        return captor.getValue();
    }
}
