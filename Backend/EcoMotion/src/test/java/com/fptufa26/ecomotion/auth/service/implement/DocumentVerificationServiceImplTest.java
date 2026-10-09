package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.verification.ekyc.EkycService;
import com.fptufa26.ecomotion.auth.exception.AccountAlreadyActiveException;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import com.fptufa26.ecomotion.auth.exception.PhoneNotVerifiedException;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.user.entity.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentVerificationServiceImplTest {

    private static final String TOKEN = "activation-token";
    private static final long USER_ID = 42L;

    @Mock
    private TokenService tokenService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EkycService ekycService;

    @Mock
    private UserService userService;

    private DocumentVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentVerificationServiceImpl(
                tokenService, fileStorageService, ekycService, userService);
    }


    @Test
    @DisplayName("scanCccd stores the front image, persists the OCR fields and returns them")
    void scanCccdPersistsOcrFields() {
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(pendingUser()));
        when(ekycService.ocrIdCard(any(), eq("cccd-front.jpg")))
                .thenReturn(new EkycService.OcrResult("NGUYỄN VĂN A", "079123456789", "1990-01-01", "Nam", "HCM"));
        when(fileStorageService.store(eq("renter"), eq(USER_ID), eq("cccd-front.jpg"), any(), any()))
                .thenReturn("renter/42/cccd-front.jpg");

        var response = service.scanCccd(TOKEN, image("cccdFront"));

        assertThat(response.scanned()).isTrue();
        assertThat(response.cccdNo()).isEqualTo("079123456789");
        assertThat(response.dateOfBirth()).isEqualTo("1990-01-01");
        assertThat(response.cccdFrontUrl()).isEqualTo("renter/42/cccd-front.jpg");

        var captor = org.mockito.ArgumentCaptor.forClass(UserService.CccdIdentity.class);
        verify(userService).saveCccdIdentity(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().cccdNo()).isEqualTo("079123456789");
    }

    @Test
    @DisplayName("scanCccd rejects an unreadable CCCD without persisting anything")
    void scanCccdRejectsUnreadableCard() {
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(pendingUser()));
        when(ekycService.ocrIdCard(any(), anyString()))
                .thenReturn(new EkycService.OcrResult("", "", null, null, null));

        assertThatThrownBy(() -> service.scanCccd(TOKEN, image("cccdFront")))
                .isInstanceOf(EkycFailedException.class);

        verify(fileStorageService, never()).store(anyString(), anyLong(), anyString(), any(), any());
        verify(userService, never()).saveCccdIdentity(anyLong(), any());
    }

    @Test
    @DisplayName("scanCccd rejects an OCR result that has a name but no CCCD number")
    void scanCccdRejectsMissingCccdNumber() {
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(pendingUser()));
        when(ekycService.ocrIdCard(any(), anyString()))
                .thenReturn(new EkycService.OcrResult("NGUYỄN VĂN A", "", "1990-01-01", "Nam", "HCM"));

        assertThatThrownBy(() -> service.scanCccd(TOKEN, image("cccdFront")))
                .isInstanceOf(EkycFailedException.class)
                .hasMessageContaining("số CCCD");

        verify(fileStorageService, never()).store(anyString(), anyLong(), anyString(), any(), any());
        verify(userService, never()).saveCccdIdentity(anyLong(), any());
    }


    @Test
    @DisplayName("verifyFace activates the account only after a match")
    void verifyFaceActivatesOnMatch() {
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(verifiedPhoneUser()));
        Renter renter = scannedRenter();
        when(userService.findRenterProfile(USER_ID)).thenReturn(Optional.of(renter));
        when(fileStorageService.read("renter/42/cccd-front.jpg")).thenReturn(new byte[]{1});
        when(ekycService.faceMatch(any(), eq("cccd-front.jpg"), any(), eq("portrait.jpg")))
                .thenReturn(new EkycService.FaceMatchResult(true, 98.7));
        when(fileStorageService.store(eq("renter"), eq(USER_ID), eq("portrait.jpg"), any(), any()))
                .thenReturn("renter/42/portrait.jpg");

        var response = service.verifyFace(TOKEN, image("portrait"));

        assertThat(response.matched()).isTrue();
        assertThat(response.activated()).isTrue();
        verify(userService).activateWithPortrait(USER_ID, "renter/42/portrait.jpg");
    }

    @Test
    @DisplayName("verifyFace does not activate the account when the faces do not match")
    void verifyFaceLeavesAccountPendingOnMismatch() {
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(verifiedPhoneUser()));
        when(userService.findRenterProfile(USER_ID)).thenReturn(Optional.of(scannedRenter()));
        when(fileStorageService.read("renter/42/cccd-front.jpg")).thenReturn(new byte[]{1});
        when(ekycService.faceMatch(any(), anyString(), any(), anyString()))
                .thenReturn(new EkycService.FaceMatchResult(false, 12.5));

        var response = service.verifyFace(TOKEN, image("portrait"));

        assertThat(response.matched()).isFalse();
        assertThat(response.activated()).isFalse();
        assertThat(response.portraitUrl()).isNull();
        verify(userService, never()).activateWithPortrait(anyLong(), anyString());
    }

    @Test
    @DisplayName("verifyFace before the phone is verified is refused")
    void verifyFaceRequiresPhone() {
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(pendingUser()));

        assertThatThrownBy(() -> service.verifyFace(TOKEN, image("portrait")))
                .isInstanceOf(PhoneNotVerifiedException.class);

        verify(ekycService, never()).faceMatch(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("verifyFace without a scanned CCCD is refused")
    void verifyFaceRequiresCccd() {
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(verifiedPhoneUser()));
        when(userService.findRenterProfile(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyFace(TOKEN, image("portrait")))
                .isInstanceOf(EkycFailedException.class);
    }

    @Test
    @DisplayName("the document steps are refused once the account is ACTIVE")
    void stepsRefusedOnceActive() {
        User active = pendingUser();
        active.setStatus(UserStatus.ACTIVE);
        when(tokenService.verifyActivation(TOKEN))
                .thenReturn(new TokenService.ActivationPrincipal(USER_ID, "a@ecomotion.vn", Role.RENTER));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.scanCccd(TOKEN, image("cccdFront")))
                .isInstanceOf(AccountAlreadyActiveException.class);
        assertThatThrownBy(() -> service.verifyFace(TOKEN, image("portrait")))
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

    private User verifiedPhoneUser() {
        User user = pendingUser();
        user.setPhone("0901234567");
        return user;
    }

    private Renter scannedRenter() {
        Renter renter = new Renter();
        renter.setCccdNo("079123456789");
        renter.setCccdImageUrl("renter/42/cccd-front.jpg");
        return renter;
    }

    private MultipartFile image(String field) {
        return new MockMultipartFile(field, "IMG_2024.jpg", "image/jpeg", "fake".getBytes());
    }
}
