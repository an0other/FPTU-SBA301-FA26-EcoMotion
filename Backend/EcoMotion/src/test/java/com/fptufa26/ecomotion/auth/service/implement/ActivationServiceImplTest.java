package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.dto.request.SendPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.response.SendPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.exception.AccountAlreadyActiveException;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.PhoneAlreadyExistsException;
import com.fptufa26.ecomotion.auth.service.OtpService;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationServiceImplTest {

    private static final String TOKEN = "activation-token";
    private static final long USER_ID = 42L;

    @Mock
    private TokenService tokenService;

    @Mock
    private OtpService otpService;

    @Mock
    private UserService userService;

    private ActivationServiceImpl service;

    private ActivationServiceImpl newService() {
        ActivationServiceImpl impl = new ActivationServiceImpl(tokenService, otpService, userService);
        ReflectionTestUtils.setField(impl, "exposeOtpInResponse", true);
        return impl;
    }


    @Test
    @DisplayName("sendPhoneOtp rejects a phone already used by another account")
    void sendPhoneOtpRejectsDuplicatePhone() {
        service = newService();
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(pendingUser()));
        when(userService.existsByPhone("0909999999")).thenReturn(true);

        assertThatThrownBy(() -> service.sendPhoneOtp(new SendPhoneOtpRequest(TOKEN, "0909999999")))
                .isInstanceOf(PhoneAlreadyExistsException.class);

        verify(otpService, never()).sendPhone(anyString(), anyString());
    }

    @Test
    @DisplayName("sendPhoneOtp re-sending to the account's own phone is allowed")
    void sendPhoneOtpAllowsOwnPhone() {
        service = newService();
        User user = pendingUser();
        user.setPhone("0901234567");
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(user));
        when(otpService.sendPhone(String.valueOf(USER_ID), "0901234567"))
                .thenReturn(new OtpService.PhoneOtpChallenge("123456", 300, 60));

        SendPhoneOtpResponse response = service.sendPhoneOtp(new SendPhoneOtpRequest(TOKEN, "0901234567"));

        assertThat(response.sent()).isTrue();
        verify(userService, never()).existsByPhone(anyString());
    }

    @Test
    @DisplayName("status reconstructs progress from the persisted profile")
    void statusReconstructsProgress() {
        service = newService();
        User user = pendingUser();
        user.setPhone("0901234567");
        user.setFullName("NGUYỄN VĂN A");
        Renter renter = new Renter();
        renter.setCccdNo("079123456789");
        renter.setDateOfBirth(LocalDate.of(1990, 1, 1));
        renter.setGender("Nam");
        renter.setAddress("HCM");
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userService.findRenterProfile(USER_ID)).thenReturn(Optional.of(renter));

        var response = service.status(TOKEN);

        assertThat(response.phoneVerified()).isTrue();
        assertThat(response.cccdScanned()).isTrue();
        assertThat(response.ekycStatus()).isEqualTo(EkycStatus.UNVERIFIED.name());
        assertThat(response.cccdNo()).isEqualTo("079123456789");
    }

    @Test
    @DisplayName("an activation token pointing at a missing user is rejected")
    void unknownUserIsRejected() {
        service = newService();
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.status(TOKEN))
                .isInstanceOf(ActivationTokenInvalidException.class);
    }

    @Test
    @DisplayName("the activation steps are refused once the account is ACTIVE")
    void stepsRefusedOnceActive() {
        service = newService();
        User active = pendingUser();
        active.setStatus(UserStatus.ACTIVE);
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.sendPhoneOtp(new SendPhoneOtpRequest(TOKEN, "0901234567")))
                .isInstanceOf(AccountAlreadyActiveException.class);
    }


    private User pendingUser() {
        User user = new User();
        user.setUserId(USER_ID);
        user.setEmail("a@ecomotion.vn");
        user.setRole(Role.RENTER);
        user.setStatus(UserStatus.PENDING);
        user.setEkycStatus(EkycStatus.UNVERIFIED);
        return user;
    }
}
