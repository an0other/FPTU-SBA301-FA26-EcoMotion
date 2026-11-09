package com.fptufa26.ecomotion.verification.identity;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.documentai.DocumentAiClient;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityVideoResult;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.integration.IdentityVerificationClient;
import com.fptufa26.ecomotion.verification.identity.service.implement.IdentityAiAssessmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityAiAssessmentServiceTest {

    private UserService users;
    private DocumentAiClient documents;
    private IdentityVerificationClient videos;
    private IdentityAiAssessmentServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        documents = mock(DocumentAiClient.class);
        videos = mock(IdentityVerificationClient.class);
        service = new IdentityAiAssessmentServiceImpl(users, documents, videos, true, 1024, 1024, 100);
        user = new User();
        user.setId(10L);
        user.setRole(Role.RENTER);
        user.setStatus(UserStatus.ACTIVE);
        when(users.findById(10L)).thenReturn(Optional.of(user));
    }

    @Test
    void comparesExactlyTheSameFrontUsedForOcrWithoutActivatingOrSavingIdentity() throws Exception {
        var image = image();
        when(documents.readCitizenId(any(), anyString(), any(), anyString())).thenReturn(Map.of(
                "documentType", "VN_CITIZEN_ID", "status", "READ_SUCCESS", "code", "PROCESSED",
                "data", Map.of("documentNumber", "001234567890", "fullName", "Test Person",
                        "dateOfBirth", "1990-01-01")));
        var report = new IdentityVideoResult(15,
                new IdentityVideoResult.MotionChallenge("PASS", List.of(), List.of()),
                new IdentityVideoResult.FaceMatching(8, 8, 0, 0.51, 0.55, 0.59, List.of()),
                "NOT_EVALUATED", false, new IdentityVideoResult.Workflow("READY_FOR_REVIEW", List.of()));
        when(videos.assess(any(), anyString(), any())).thenReturn(report);

        var result = service.assess(10L, image, image, video());

        var ocrFront = ArgumentCaptor.forClass(byte[].class);
        var videoFront = ArgumentCaptor.forClass(byte[].class);
        verify(documents).readCitizenId(ocrFront.capture(), anyString(), any(), anyString());
        verify(videos).assess(videoFront.capture(), anyString(), any());
        assertArrayEquals(ocrFront.getValue(), videoFront.getValue());
        assertTrue(result.assessmentOnly());
        assertFalse(result.automaticApproval());
        assertEquals("NOT_EVALUATED", result.identityDecision());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void stopsBeforeVideoWhenDocumentIsUnreadable() throws Exception {
        when(documents.readCitizenId(any(), anyString(), any(), anyString())).thenReturn(Map.of(
                "documentType", "VN_CITIZEN_ID", "status", "RETAKE_REQUIRED", "code", "PROCESSED"));
        var result = service.assess(10L, image(), image(), video());
        assertEquals("RETRY_REQUIRED", result.workflowStatus());
        verifyNoInteractions(videos);
    }

    @Test
    void refusesLockedAccountBeforeUploadingToAi() {
        user.setStatus(UserStatus.LOCKED);
        assertThrows(AccessDeniedException.class, () -> service.assess(10L, null, null, null));
        verifyNoInteractions(documents, videos);
    }

    @Test
    void rejectsFakeImageBeforeCallingAi() {
        var fake = new MockMultipartFile("cccdFront", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});
        var error = assertThrows(IdentityAiException.class, () -> service.assess(10L, fake, fake, video()));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.getStatus());
        verifyNoInteractions(documents, videos);
    }

    @Test
    void rejectsOversizedVideoBeforeCallingAi() throws Exception {
        var image = image();
        var large = new MockMultipartFile("video", "capture.mp4", "video/mp4", new byte[1025]);
        var error = assertThrows(IdentityAiException.class, () -> service.assess(10L, image, image, large));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatus());
        verifyNoInteractions(documents, videos);
    }

    private MockMultipartFile image() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("image", "image.png", "image/png", output.toByteArray());
    }

    private MockMultipartFile video() {
        return new MockMultipartFile("video", "capture.mp4", "video/mp4", new byte[]{1, 2, 3});
    }
}
