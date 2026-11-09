package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.captcha.CaptchaVerifier;
import com.fptufa26.ecomotion.auth.dto.request.RegisterRequest;
import com.fptufa26.ecomotion.auth.exception.UnsupportedRoleException;
import com.fptufa26.ecomotion.auth.service.implement.RegistrationServiceImpl;
import com.fptufa26.ecomotion.otp.service.OtpService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegistrationProviderTest {
    private final UserService users = mock(UserService.class);
    private final TokenService tokens = mock(TokenService.class);
    private final RegistrationServiceImpl service = new RegistrationServiceImpl(mock(OtpService.class), tokens,
            users, mock(CaptchaVerifier.class));

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "passwordMinLength", 8);
        when(tokens.verifyRegistration("registered-email")).thenReturn(new TokenService.RegistrationPrincipal("test@example.test"));
        var user = new User();
        user.setId(1L);
        when(users.createPendingUser(anyString(), anyString(), any())).thenReturn(user);
        when(tokens.issueActivation(anyLong(), anyString(), any())).thenReturn("activation");
    }

    @Test
    void providerRegistrationDoesNotCreateRenterProfile() {
        assertNotNull(service.register(new RegisterRequest("registered-email", "password123", Role.PROVIDER, true), null, null));
        verify(users).createPendingUser("test@example.test", "password123", Role.PROVIDER);
        verify(users, never()).createRenterProfile(any());
    }

    @Test
    void renterRegistrationStillCreatesRenterProfile() {
        service.register(new RegisterRequest("registered-email", "password123", Role.RENTER, true), null, null);
        verify(users).createRenterProfile(any());
    }

    @Test
    void registrationCannotChooseAdminOrDriver() {
        for (Role role : new Role[]{Role.ADMIN, Role.DRIVER}) {
            assertThrows(UnsupportedRoleException.class, () -> service.register(
                    new RegisterRequest("registered-email", "password123", role, true), null, null));
        }
        verify(users, never()).createPendingUser(anyString(), anyString(), any());
    }
}
