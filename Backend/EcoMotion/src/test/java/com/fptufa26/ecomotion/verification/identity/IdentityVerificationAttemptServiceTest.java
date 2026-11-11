package com.fptufa26.ecomotion.verification.identity;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.entity.IdentityVerificationAttempt;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.repository.IdentityVerificationAttemptRepository;
import com.fptufa26.ecomotion.verification.identity.service.IdentityAiAssessmentService;
import com.fptufa26.ecomotion.verification.identity.service.IdentityEvidenceStorageService;
import com.fptufa26.ecomotion.verification.identity.service.implement.IdentityVerificationAttemptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityVerificationAttemptServiceTest {
    private final IdentityVerificationAttemptRepository repository = mock(IdentityVerificationAttemptRepository.class);
    private final UserService users = mock(UserService.class);
    private final IdentityEvidenceStorageService storage = mock(IdentityEvidenceStorageService.class);
    private final IdentityAiAssessmentService ai = mock(IdentityAiAssessmentService.class);
    private final Instant now = Instant.parse("2026-11-09T00:00:00Z");
    private IdentityVerificationAttemptServiceImpl service;
    private IdentityVerificationAttempt attempt;

    @BeforeEach
    void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        service = new IdentityVerificationAttemptServiceImpl(repository, users, storage, ai,
                new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC), manager,
                Duration.ofHours(24), Duration.ofMinutes(10), 100, 200);
        var user = new User();
        user.setId(1L);
        user.setRole(Role.RENTER);
        user.setStatus(UserStatus.PENDING);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        attempt = new IdentityVerificationAttempt();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setUser(user);
        attempt.setActiveUserId(1L);
        attempt.setRole(Role.RENTER);
        attempt.setStatus(DRAFT);
        attempt.setVersion(2L);
        attempt.setExpiresAt(now.plusSeconds(3600));
        when(repository.findOwnedForUpdate(attempt.getPublicId(), 1L)).thenReturn(Optional.of(attempt));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsStaleVersionBeforeWritingEvidence() {
        assertThrows(IdentityAiException.class, () -> service.uploadDocuments(1L,
                attempt.getPublicId(), 1, new MockMultipartFile("front", new byte[]{1}),
                new MockMultipartFile("back", new byte[]{2})));
        verifyNoInteractions(storage, ai);
    }

    @Test
    void deniesEvidenceOfAnotherOwnerBeforeStorageRead() {
        when(repository.findOwnedForUpdate(attempt.getPublicId(), 1L)).thenReturn(Optional.empty());
        assertThrows(IdentityAiException.class, () -> service.evidence(1L, attempt.getPublicId(), "front"));
        verifyNoInteractions(storage);
    }

    @Test
    void expiresAttemptAndReleasesActiveSlot() {
        attempt.setExpiresAt(now);
        var response = service.get(1L, attempt.getPublicId());
        assertEquals(EXPIRED, response.status());
        assertNull(attempt.getActiveUserId());
        assertFalse(response.canSubmit());
    }

    @Test
    void recoversAbandonedOperationWithoutApprovingIdentity() {
        attempt.setStatus(PROCESSING);
        attempt.setProcessingStage("VIDEO");
        attempt.setOperationId(UUID.randomUUID());
        attempt.setOperationStartedAt(now.minusSeconds(601));
        var response = service.get(1L, attempt.getPublicId());
        assertEquals(ERROR, response.status());
        assertEquals("RETRY_VIDEO", response.nextAction());
        assertEquals("IDENTITY_PROCESSING_TIMEOUT", response.lastErrorCode());
        assertNull(attempt.getOperationId());
        assertFalse(response.automaticApproval());
    }

    @Test
    void documentReplacementInvalidatesPreviousVideo() {
        attempt.setVideoKey("old-video");
        attempt.setVideoJson("{}");
        when(storage.store(any(), any(), eq(100))).thenReturn("new-front", "new-back");
        when(storage.read(anyString(), eq(100))).thenReturn(new byte[]{1});
        when(ai.readDocuments(eq(1L), any(), any())).thenReturn(readableDocument());
        var response = service.uploadDocuments(1L, attempt.getPublicId(), 2,
                new MockMultipartFile("front", new byte[]{1}), new MockMultipartFile("back", new byte[]{2}));
        assertEquals(DOCUMENT_READY, response.status());
        assertEquals(1, response.documentRevision());
        assertFalse(response.hasVideo());
        assertNull(attempt.getVideoJson());
        verify(storage).delete("old-video");
    }

    @Test
    void lateDocumentResultCannotOverwriteCancelledAttempt() {
        when(storage.store(any(), any(), eq(100))).thenReturn("new-front", "new-back");
        when(storage.read(anyString(), eq(100))).thenReturn(new byte[]{1});
        when(ai.readDocuments(eq(1L), any(), any())).thenAnswer(invocation -> {
            service.cancel(1L, attempt.getPublicId(), 2);
            return readableDocument();
        });
        assertThrows(IdentityAiException.class, () -> service.uploadDocuments(1L, attempt.getPublicId(), 2,
                new MockMultipartFile("front", new byte[]{1}), new MockMultipartFile("back", new byte[]{2})));
        assertEquals(CANCELLED, attempt.getStatus());
        assertNull(attempt.getDocumentJson());
        assertNull(attempt.getOperationId());
    }

    private com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiAssessmentResponse.DocumentRead readableDocument() {
        return new com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiAssessmentResponse.DocumentRead(
                "VN_CITIZEN_ID", "SUCCESS", null, "000000000001", "Test", "2000-01-01", null, null, null, true);
    }

    @Test
    void cancelInvalidatesInFlightOperation() {
        attempt.setStatus(PROCESSING);
        attempt.setOperationId(UUID.randomUUID());
        var response = service.cancel(1L, attempt.getPublicId(), 2);
        assertEquals(CANCELLED, response.status());
        assertNull(attempt.getOperationId());
        assertNull(attempt.getActiveUserId());
    }
}
