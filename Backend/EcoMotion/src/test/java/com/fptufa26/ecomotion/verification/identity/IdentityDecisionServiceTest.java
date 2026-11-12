package com.fptufa26.ecomotion.verification.identity;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.dto.response.*;
import com.fptufa26.ecomotion.verification.identity.entity.*;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.repository.*;
import com.fptufa26.ecomotion.verification.identity.service.*;
import com.fptufa26.ecomotion.verification.identity.service.implement.IdentityDecisionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IdentityDecisionServiceTest {
    private final IdentityVerificationAttemptRepository attempts = mock(IdentityVerificationAttemptRepository.class);
    private final PersonIdentityRepository identities = mock(PersonIdentityRepository.class);
    private final UserService users = mock(UserService.class);
    private final IdentityFingerprintService fingerprints = mock(IdentityFingerprintService.class);
    private final IdentityVerificationAttemptService sessions = mock(IdentityVerificationAttemptService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
    private User user;
    private IdentityVerificationAttempt attempt;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId(1L);
        user.setRole(Role.PROVIDER);
        user.setPhone("0900000000");
        user.setPhoneVerifiedAt(clock.instant());
        user.setStatus(UserStatus.PENDING);
        attempt = new IdentityVerificationAttempt();
        attempt.setId(10L);
        attempt.setPublicId(UUID.randomUUID());
        attempt.setUser(user);
        attempt.setRole(user.getRole());
        attempt.setStatus(READY_TO_SUBMIT);
        attempt.setVersion(3L);
        attempt.setExpiresAt(clock.instant().plusSeconds(3600));
        attempt.setFrontKey("front");
        attempt.setBackKey("back");
        attempt.setVideoKey("video");
        attempt.setDocumentJson(mapper.writeValueAsString(new IdentityAiAssessmentResponse.DocumentRead(
                "VN_CITIZEN_ID", "SUCCESS", null, "000000000001", "Test", "01/01/2000", "01/01/2040", null, null, true)));
        attempt.setVideoJson(mapper.writeValueAsString(new IdentityVideoResult(10,
                new IdentityVideoResult.MotionChallenge("PASS", List.of(), List.of()),
                new IdentityVideoResult.FaceMatching(8, 8, 0, .5, .6, .7, List.of()),
                "NOT_EVALUATED", false, new IdentityVideoResult.Workflow("READY_FOR_REVIEW", List.of()))));
        when(attempts.findOwnedForUpdate(attempt.getPublicId(), 1L)).thenReturn(Optional.of(attempt));
        when(attempts.findPublicForUpdate(attempt.getPublicId())).thenReturn(Optional.of(attempt));
        when(attempts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.lockForIdentityDecision(1L)).thenReturn(user);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        var admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(fingerprints.normalizeCccdNumber(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fingerprints.fingerprint(anyString())).thenReturn("a".repeat(64));
        when(fingerprints.keyVersion()).thenReturn(1);
        when(identities.saveAndFlush(any())).thenAnswer(invocation -> {
            PersonIdentity identity = invocation.getArgument(0);
            identity.setId(20L);
            return identity;
        });
    }

    private IdentityDecisionServiceImpl service(boolean configured) {
        return new IdentityDecisionServiceImpl(attempts, identities, users, fingerprints, sessions, mapper, clock,
                configured ? "0.55" : "");
    }

    @Test
    void providerPreparationDoesNotQueueSeparateAdminReview() {
        var result = service(true).submit(1L, attempt.getPublicId(), 3, true);
        assertEquals(READY_TO_SUBMIT, result.status());
        assertFalse(result.accountActivated());
        verifyNoInteractions(identities);
        verify(users, never()).applyVerifiedIdentity(anyLong(), any());
    }

    @Test
    void providerLowSimilarityRemainsAvailableForCombinedManualReview() {
        var strict = new IdentityDecisionServiceImpl(attempts, identities, users, fingerprints, sessions, mapper, clock, "0.9");
        assertEquals(READY_TO_SUBMIT, strict.submit(1L, attempt.getPublicId(), 3, true).status());
        verifyNoInteractions(identities);
    }

    @Test
    void combinedSubmissionQueuesAndPinsPreparedProviderEvidence() {
        when(attempts.findFirstByUser_IdOrderByIdDesc(1L)).thenReturn(Optional.of(attempt));
        assertEquals(attempt.getPublicId(), service(true).submitProviderEvidence(1L));
        assertEquals(SUBMITTED, attempt.getStatus());
        assertEquals(1, attempt.getReviewHistory().size());
        assertEquals("SUBMIT", attempt.getReviewHistory().getFirst().getEventType());
        assertEquals("front", attempt.getReviewHistory().getFirst().getFrontKey());
        assertTrue(attempt.isEvidenceRetained());
        verify(users, never()).applyVerifiedIdentity(anyLong(), any());
    }

    @Test
    void providerManualPreparationDoesNotRequireAutomatedThreshold() {
        assertEquals(READY_TO_SUBMIT, service(false).submit(1L, attempt.getPublicId(), 3, true).status());
        verifyNoInteractions(identities);
    }

    @Test
    void rejectsInvalidThresholdsAtStartup() {
        for (String value : List.of("2", "0", "-0.1", "NaN", "Infinity", "invalid")) {
            assertThrows(IllegalArgumentException.class, () -> new IdentityDecisionServiceImpl(
                    attempts, identities, users, fingerprints, sessions, mapper, clock, value));
        }
    }

    @Test
    void renterCanQueueWithoutAutomatedThreshold() {
        user.setRole(Role.RENTER);
        attempt.setRole(Role.RENTER);
        assertEquals(SUBMITTED, service(false).submit(1L, attempt.getPublicId(), 3, true).status());
        verify(users, never()).applyVerifiedIdentity(anyLong(), any());
    }

    @Test
    void renterPassingPolicyStillRequiresAdminDecision() {
        user.setRole(Role.RENTER);
        attempt.setRole(Role.RENTER);
        var result = service(true).submit(1L, attempt.getPublicId(), 3, true);
        assertEquals(SUBMITTED, result.status());
        assertNull(result.actor());
        assertFalse(result.accountActivated());
        verify(users, never()).applyVerifiedIdentity(anyLong(), any());
        var approved = service(true).review(2L, attempt.getPublicId(), 3, "APPROVE", null);
        assertEquals(APPROVED, approved.status());
        assertEquals("ADMIN", approved.actor());
        verify(users).applyVerifiedIdentity(eq(1L), any());
    }

    @Test
    void lowSimilarityRenterGoesToManualReview() {
        user.setRole(Role.RENTER);
        attempt.setRole(Role.RENTER);
        var strict = new IdentityDecisionServiceImpl(attempts, identities, users, fingerprints, sessions, mapper, clock,
                "0.9");
        assertEquals(SUBMITTED, strict.submit(1L, attempt.getPublicId(), 3, true).status());
        verifyNoInteractions(identities);
    }

    @Test
    void renterSupplementRequiresFreshVideoAndStaleReviewIsRejected() {
        user.setRole(Role.RENTER);
        attempt.setRole(Role.RENTER);
        attempt.setVideoSha256("old-video");
        service(false).submit(1L, attempt.getPublicId(), 3, true);
        assertThrows(IdentityAiException.class, () -> service(false).review(2L, attempt.getPublicId(), 2, "APPROVE", null));
        var invalid = assertThrows(IdentityAiException.class, () -> service(false).reviewRenter(
                2L, attempt.getPublicId(), 3, "NEEDS_CHANGES", "FACE_UNCLEAR", " "));
        assertEquals("IDENTITY_REVIEW_NOTE_REQUIRED", invalid.getErrorCode());
        assertEquals(1, attempt.getReviewHistory().size());
        assertEquals(NEEDS_CHANGES, service(false).reviewRenter(2L, attempt.getPublicId(), 3,
                "NEEDS_CHANGES", "FACE_UNCLEAR", "Vui lòng quay lại video ở nơi đủ sáng.").status());
        assertEquals(2, attempt.getReviewHistory().size());
        var review = attempt.getReviewHistory().get(1);
        assertEquals("Vui lòng quay lại video ở nơi đủ sáng.", review.getNote());
        assertEquals("old-video", review.getVideoSha256());
        assertTrue(attempt.isEvidenceRetained());
        assertThrows(AccessDeniedException.class, () -> service(false).historyEvidence(1L,
                attempt.getPublicId(), 1L, "front"));
        attempt.setStatus(READY_TO_SUBMIT);
        assertEquals("IDENTITY_NEW_EVIDENCE_REQUIRED", assertThrows(IdentityAiException.class,
                () -> service(false).submit(1L, attempt.getPublicId(), 3, true)).getErrorCode());
        attempt.setVideoSha256("new-video");
        assertEquals(SUBMITTED, service(false).submit(1L, attempt.getPublicId(), 3, true).status());
        assertEquals(3, attempt.getReviewHistory().size());
        assertEquals("old-video", review.getVideoSha256());
        assertEquals("new-video", attempt.getReviewHistory().get(2).getVideoSha256());
    }

    @Test
    void sameCccdOtherRoleCanBeReviewedWithoutAccountLink() {
        attempt.setStatus(SUBMITTED);
        var existing = new PersonIdentity();existing.setId(20L);existing.setFullName("Test");existing.setDateOfBirth(LocalDate.of(2000,1,1));existing.setStatus(com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED);
        when(identities.findByCccdFingerprint(anyString())).thenReturn(Optional.of(existing));
        when(identities.findByIdForUpdate(20L)).thenReturn(Optional.of(existing));
        assertEquals(APPROVED, service(false).review(2L, attempt.getPublicId(), 3, "APPROVE", null).status());
        verify(users).applyVerifiedIdentity(1L, existing);
        verify(identities, never()).saveAndFlush(any());
    }

    @Test
    void providerAdminApprovalVerifiesIdentityOnly() {
        attempt.setStatus(SUBMITTED);
        var result = service(false).review(2L, attempt.getPublicId(), 3, "APPROVE", null);
        assertEquals(APPROVED, result.status());
        assertEquals("ADMIN", result.actor());
        assertFalse(result.accountActivated());
        assertEquals(2L, attempt.getReviewedBy());
    }

    @Test
    void approvalUsesPersistedIdentityReturnedByRepository() {
        attempt.setStatus(SUBMITTED);
        var persisted = new PersonIdentity();
        persisted.setId(30L);
        persisted.setStatus(com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus.VERIFIED);
        doAnswer(invocation -> {
            PersonIdentity original = invocation.getArgument(0);
            assertNull(original.getId());
            assertNull(original.getVersion());
            return persisted;
        }).when(identities).saveAndFlush(any());
        service(false).review(2L, attempt.getPublicId(), 3, "APPROVE", null);
        verify(users).applyVerifiedIdentity(1L, persisted);
        assertEquals(30L, attempt.getResultingIdentityId());
        assertEquals(APPROVED, attempt.getStatus());
    }

    @Test
    void rejectsStaleVersionAndUnconfirmedSubmission() {
        assertThrows(IdentityAiException.class, () -> service(false).submit(1L, attempt.getPublicId(), 2, true));
        assertThrows(IdentityAiException.class, () -> service(false).submit(1L, attempt.getPublicId(), 3, false));
        verifyNoInteractions(identities);
    }

    @Test
    void repeatedSubmitDoesNotCreateSecondIdentity() {
        attempt.setStatus(SUBMITTED);
        assertEquals(SUBMITTED, service(false).submit(1L, attempt.getPublicId(), 2, true).status());
        verifyNoInteractions(identities);
    }

    @Test
    void nonAdminCannotReview() {
        when(users.findById(1L)).thenReturn(Optional.of(user));
        assertThrows(AccessDeniedException.class, () -> service(false).review(1L, attempt.getPublicId(), 3, "APPROVE", null));
    }

    @Test
    void requestedVideoReplacementMustChangeBeforeResubmission() {
        attempt.setRequiredPreviousVideoSha("old-video-hash");
        attempt.setVideoSha256("old-video-hash");
        var error = assertThrows(IdentityAiException.class, () -> service(true).submit(1L, attempt.getPublicId(), 3, true));
        assertEquals("IDENTITY_NEW_EVIDENCE_REQUIRED", error.getErrorCode());
        attempt.setVideoSha256("new-video-hash");
        assertEquals(READY_TO_SUBMIT, service(true).submit(1L, attempt.getPublicId(), 3, true).status());
    }

    @Test
    void readinessUsesMotionAndWorkflowWithoutMakingIdentityDecision() {
        when(attempts.findFirstByUser_IdOrderByIdDesc(1L)).thenReturn(Optional.of(attempt));
        assertTrue(service(true).providerEvidenceReady(1L));
        var strict = new IdentityDecisionServiceImpl(attempts, identities, users, fingerprints, sessions, mapper, clock, "0.9");
        assertTrue(strict.providerEvidenceReady(1L));
    }
}
