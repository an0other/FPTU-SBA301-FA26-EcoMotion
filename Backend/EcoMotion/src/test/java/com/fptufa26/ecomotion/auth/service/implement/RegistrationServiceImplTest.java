package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.captcha.CaptchaVerifier;
import com.fptufa26.ecomotion.auth.dto.request.RegisterRequest;
import com.fptufa26.ecomotion.auth.dto.response.RegisterResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyOtpResponse;
import com.fptufa26.ecomotion.auth.exception.DuplicateEmailException;
import com.fptufa26.ecomotion.auth.exception.UnsupportedRoleException;
import com.fptufa26.ecomotion.auth.service.OtpService;
import com.fptufa26.ecomotion.auth.service.TokenService;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    private static final String EMAIL = "newrenter@ecomotion.vn";
    private static final String PASSWORD = "P@ssw0rd123";
    private static final String TOKEN = "registration-token";
    private static final String ACTIVATION_TOKEN = "activation-token";

    @Mock
    private OtpService otpService;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserService userService;

    @Mock
    private CaptchaVerifier captchaVerifier;

    private RegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegistrationServiceImpl(
                otpService, tokenService, userService, captchaVerifier);
        ReflectionTestUtils.setField(service, "exposeOtpInResponse", true);
        ReflectionTestUtils.setField(service, "passwordMinLength", 8);
    }


    @Test
    @DisplayName("sendOtp returns the challenge and echoes the code when expose-in-response is on")
    void sendOtpEchoesCode() {
        when(otpService.send(EMAIL)).thenReturn(new OtpService.OtpChallenge(EMAIL, "123456", 300, 60));

        SendOtpResponse response = service.sendOtp(EMAIL);

        assertThat(response.sent()).isTrue();
        assertThat(response.otp()).isEqualTo("123456");
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(response.resendCooldownSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("sendOtp hides the code when expose-in-response is off")
    void sendOtpHidesCodeWhenNotExposed() {
        ReflectionTestUtils.setField(service, "exposeOtpInResponse", false);
        when(otpService.send(EMAIL)).thenReturn(new OtpService.OtpChallenge(EMAIL, "123456", 300, 60));

        SendOtpResponse response = service.sendOtp(EMAIL);

        assertThat(response.otp()).isNull();
    }

    @Test
    @DisplayName("sendOtp rejects an already-registered email before spending an OTP")
    void sendOtpRejectsDuplicateEmail() {
        when(userService.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.sendOtp(EMAIL))
                .isInstanceOf(DuplicateEmailException.class);

        verify(otpService, never()).send(anyString());
    }

    @Test
    @DisplayName("verifyOtp consumes the code and issues a registration token")
    void verifyOtpIssuesToken() {
        when(tokenService.issueRegistration(EMAIL)).thenReturn(TOKEN);
        when(tokenService.getRegistrationTtlSeconds()).thenReturn(1800L);

        VerifyOtpResponse response = service.verifyOtp(EMAIL, "123456");

        verify(otpService).verify(EMAIL, "123456");
        assertThat(response.registrationToken()).isEqualTo(TOKEN);
        assertThat(response.expiresInSeconds()).isEqualTo(1800L);
    }


    @Test
    @DisplayName("register creates a PENDING user + renter profile and returns an activation token")
    void registerCreatesPendingAccount() {
        when(tokenService.verifyRegistration(TOKEN))
                .thenReturn(new TokenService.RegistrationPrincipal(EMAIL));
        when(userService.existsByEmail(EMAIL)).thenReturn(false);
        User saved = pendingUser();
        when(userService.createPendingUser(EMAIL, PASSWORD, Role.RENTER)).thenReturn(saved);
        when(tokenService.issueActivation(42L, EMAIL, Role.RENTER)).thenReturn(ACTIVATION_TOKEN);
        when(tokenService.getActivationTtlSeconds()).thenReturn(3600L);

        RegisterResponse response = service.register(renterRequest(), "127.0.0.1", "JUnit");

        verify(captchaVerifier).verify(null, "register", "127.0.0.1");
        verify(userService).createRenterProfile(saved);
        assertThat(response.created()).isTrue();
        assertThat(response.activationToken()).isEqualTo(ACTIVATION_TOKEN);
        assertThat(response.activationTokenExpiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("register forwards the captcha token to the verifier")
    void registerVerifiesCaptchaToken() {
        when(tokenService.verifyRegistration(TOKEN))
                .thenReturn(new TokenService.RegistrationPrincipal(EMAIL));
        when(userService.existsByEmail(EMAIL)).thenReturn(false);
        when(userService.createPendingUser(EMAIL, PASSWORD, Role.RENTER)).thenReturn(pendingUser());
        when(tokenService.issueActivation(any(), anyString(), any())).thenReturn(ACTIVATION_TOKEN);

        service.register(
                new RegisterRequest(TOKEN, PASSWORD, Role.RENTER, true, "captcha-abc"), "ip", "ua");

        verify(captchaVerifier).verify("captcha-abc", "register", "ip");
    }


    @Test
    @DisplayName("DRIVER, ADMIN and PROVIDER roles are rejected before any write")
    void rejectsUnsupportedRoles() {
        when(tokenService.verifyRegistration(TOKEN))
                .thenReturn(new TokenService.RegistrationPrincipal(EMAIL));

        for (Role role : new Role[]{Role.DRIVER, Role.ADMIN, Role.PROVIDER}) {
            RegisterRequest request = new RegisterRequest(TOKEN, PASSWORD, role, true);
            assertThatThrownBy(() -> service.register(request, "ip", "ua"))
                    .isInstanceOf(UnsupportedRoleException.class);
        }
        verify(userService, never()).createPendingUser(any(), any(), any());
    }

    @Test
    @DisplayName("terms must be accepted")
    void rejectsMissingTerms() {
        when(tokenService.verifyRegistration(TOKEN))
                .thenReturn(new TokenService.RegistrationPrincipal(EMAIL));

        RegisterRequest request = new RegisterRequest(TOKEN, PASSWORD, Role.RENTER, false);

        assertThatThrownBy(() -> service.register(request, "ip", "ua"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Terms");
    }

    @Test
    @DisplayName("a password shorter than the minimum is rejected")
    void rejectsShortPassword() {
        when(tokenService.verifyRegistration(TOKEN))
                .thenReturn(new TokenService.RegistrationPrincipal(EMAIL));

        RegisterRequest request = new RegisterRequest(TOKEN, "short", Role.RENTER, true);

        assertThatThrownBy(() -> service.register(request, "ip", "ua"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password");
    }

    @Test
    @DisplayName("duplicate email is rejected with a 409-mapped exception")
    void rejectsDuplicateEmail() {
        when(tokenService.verifyRegistration(TOKEN))
                .thenReturn(new TokenService.RegistrationPrincipal(EMAIL));
        when(userService.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.register(renterRequest(), "ip", "ua"))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userService, never()).createPendingUser(any(), any(), any());
    }

    @Test
    @DisplayName("the account is created with the RENTER role")
    void createsAccountWithRequestedRole() {
        when(tokenService.verifyRegistration(TOKEN))
                .thenReturn(new TokenService.RegistrationPrincipal(EMAIL));
        when(userService.existsByEmail(EMAIL)).thenReturn(false);
        when(userService.createPendingUser(EMAIL, PASSWORD, Role.RENTER)).thenReturn(pendingUser());
        when(tokenService.issueActivation(any(), anyString(), any())).thenReturn(ACTIVATION_TOKEN);

        service.register(renterRequest(), "ip", "ua");

        ArgumentCaptor<Role> role = ArgumentCaptor.forClass(Role.class);
        verify(userService).createPendingUser(eq(EMAIL), eq(PASSWORD), role.capture());
        assertThat(role.getValue()).isEqualTo(Role.RENTER);
    }


    private RegisterRequest renterRequest() {
        return new RegisterRequest(TOKEN, PASSWORD, Role.RENTER, true);
    }

    private User pendingUser() {
        User user = new User();
        user.setUserId(42L);
        user.setEmail(EMAIL);
        user.setRole(Role.RENTER);
        user.setStatus(UserStatus.PENDING);
        return user;
    }
}
