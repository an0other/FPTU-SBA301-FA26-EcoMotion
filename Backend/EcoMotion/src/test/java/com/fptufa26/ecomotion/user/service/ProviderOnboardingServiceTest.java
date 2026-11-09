package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.dto.request.ProviderApplicationRequest;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.repository.*;
import com.fptufa26.ecomotion.user.service.implement.ProviderOnboardingServiceImpl;
import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;
import com.fptufa26.ecomotion.verification.identity.enums.*;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus.*;

class ProviderOnboardingServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ProviderRepository providers = mock(ProviderRepository.class);
    private final ProviderApplicationRepository applications = mock(ProviderApplicationRepository.class);
    private final ProviderApplicationReviewRepository reviews = mock(ProviderApplicationReviewRepository.class);
    private final com.fptufa26.ecomotion.verification.identity.service.IdentityDecisionService identityDecisions = mock(com.fptufa26.ecomotion.verification.identity.service.IdentityDecisionService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
    private ValidatorFactory factory;
    private ProviderOnboardingServiceImpl service;
    private User user;
    private ProviderApplication application;

    @BeforeEach
    void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new ProviderOnboardingServiceImpl(users, providers, applications, reviews, mapper,
                factory.getValidator(), clock, identityDecisions, new BigDecimal("0.10"), new BigDecimal("0.30"));
        user = new User();
        user.setId(1L);
        user.setRole(Role.PROVIDER);
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerifiedAt(clock.instant());
        user.setPhone("0900000000");
        user.setPhoneVerifiedAt(clock.instant());
        user.setIdentityReviewedAt(clock.instant());
        user.setTermsAcceptedAt(clock.instant());
        var identity = new PersonIdentity();
        identity.setId(8L);
        identity.setStatus(PersonIdentityStatus.VERIFIED);
        identity.setVerificationMethod(IdentityVerificationMethod.ADMIN_REVIEW);
        user.setIdentity(identity);
        var admin = new User();
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(user));
        application = new ProviderApplication();
        application.setUserId(1L);
        application.setVersion(3L);
        application.setProfileJson(mapper.writeValueAsString(request(3L)));
        when(applications.findById(1L)).thenReturn(Optional.of(application));
        when(applications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void close() { factory.close(); }

    private ProviderApplicationRequest request(long version) {
        return new ProviderApplicationRequest(version, "Provider Test", "0900000000", "Test address",
                new BigDecimal("10.123456"), new BigDecimal("106.123456"), null, null, null);
    }

    @Test
    void savingDraftDoesNotCreateOperationalProvider() {
        assertEquals(DRAFT, service.save(1L, request(3)).status());
        verifyNoInteractions(providers);
        assertEquals(UserStatus.PENDING, user.getStatus());
    }

    @Test
    void rejectsStaleAndSubmittedEdits() {
        assertThrows(IdentityAiException.class, () -> service.save(1L, request(2)));
        application.setStatus(SUBMITTED);
        assertThrows(IdentityAiException.class, () -> service.save(1L, request(3)));
    }

    @Test
    void cannotSubmitWithoutPreparedEvidence() {
        user.setIdentityReviewedAt(null);
        when(identityDecisions.submitProviderEvidence(1L)).thenThrow(new IdentityAiException(org.springframework.http.HttpStatus.CONFLICT, "IDENTITY_EVIDENCE_INCOMPLETE", "Missing evidence"));
        assertThrows(IdentityAiException.class, () -> service.submit(1L, 3));
        assertEquals(DRAFT, application.getStatus());
    }

    @Test
    void phoneStringAloneDoesNotProveOtp() {
        user.setPhoneVerifiedAt(null);
        assertThrows(IdentityAiException.class, () -> service.submit(1L, 3));
    }

    @Test
    void submitPinsIdentityAndKeepsUserPending() {
        var result = service.submit(1L, 3);
        assertEquals(SUBMITTED, result.status());
        assertEquals(8L, application.getSubmittedIdentityId());
        assertEquals(UserStatus.PENDING, user.getStatus());
        verifyNoInteractions(providers);
    }

    @Test
    void adminApprovalCreatesIndividualProviderAndActivatesUser() {
        service.submit(1L, 3);
        var result = service.review(2L, 1L, 3, "APPROVE", null);
        assertEquals(APPROVED, result.status());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(providers).saveAndFlush(argThat(provider -> provider.getProviderType() == ProviderType.INDIVIDUAL
                && provider.getStatus() == ProviderStatus.APPROVED && provider.getDepotLatitude() != null));
        verify(reviews).save(argThat(review -> "APPROVE".equals(review.getDecision())
                && review.getProfileSnapshotJson() != null));
    }

    @Test
    void changedIdentityCannotBeApproved() {
        service.submit(1L, 3);
        user.getIdentity().setId(9L);
        assertThrows(IdentityAiException.class, () -> service.review(2L, 1L, 3, "APPROVE", null));
        verify(providers, never()).saveAndFlush(any());
    }

    @Test
    void needsChangesCanBeEditedWithoutCreatingProvider() {
        service.submit(1L, 3);
        assertEquals(NEEDS_CHANGES, service.review(2L, 1L, 3, "NEEDS_CHANGES", "ADDRESS_REQUIRED").status());
        assertTrue(service.get(1L).canEdit());
        assertEquals(DRAFT, service.save(1L, request(3)).status());
        verifyNoInteractions(providers);
    }

    @Test
    void rejectedApplicationCanBeEditedAndResubmitted() {
        service.submit(1L, 3);
        service.review(2L, 1L, 3, "REJECT", "INVALID_PROFILE");
        assertTrue(service.get(1L).canEdit());
        assertEquals(DRAFT, service.save(1L, request(3)).status());
        assertEquals(SUBMITTED, service.submit(1L, 3).status());
    }

    @Test
    void combinedReviewVerifiesIdentityAndActivatesProviderTogether() {
        user.setIdentity(null);
        user.setIdentityReviewedAt(null);
        var attemptId = UUID.randomUUID();
        when(identityDecisions.submitProviderEvidence(1L)).thenReturn(attemptId);
        when(identityDecisions.providerEvidenceReady(1L)).thenReturn(true);
        assertEquals(SUBMITTED, service.submit(1L, 3).status());
        assertEquals(attemptId, application.getSubmittedAttemptId());
        assertEquals(UserStatus.PENDING, user.getStatus());
        doAnswer(invocation -> {
            var identity = new PersonIdentity();
            identity.setId(8L);
            identity.setStatus(PersonIdentityStatus.VERIFIED);
            user.setIdentity(identity);
            user.setIdentityReviewedAt(clock.instant());
            return null;
        }).when(identityDecisions).reviewProviderEvidence(2L, 1L, attemptId, "APPROVE", null);
        assertEquals(APPROVED, service.review(2L, 1L, 3, "APPROVE", null).status());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(identityDecisions).reviewProviderEvidence(2L, 1L, attemptId, "APPROVE", null);
        verify(providers).saveAndFlush(any());
    }

    @Test
    void renterAndLockedUserCannotUseProviderOnboarding() {
        user.setRole(Role.RENTER);
        assertThrows(AccessDeniedException.class, () -> service.save(1L, request(3)));
        user.setRole(Role.PROVIDER);
        user.setStatus(UserStatus.LOCKED);
        assertThrows(AccessDeniedException.class, () -> service.save(1L, request(3)));
    }

    @Test
    void failedIdentityApprovalDoesNotCreateOperationalProvider() {
        user.setIdentity(null);
        user.setIdentityReviewedAt(null);
        var attemptId = UUID.randomUUID();
        application.setStatus(SUBMITTED);
        application.setSubmittedAttemptId(attemptId);
        doThrow(new IdentityAiException(org.springframework.http.HttpStatus.CONFLICT, "IDENTITY_LINK_REQUIRED", "Link required"))
                .when(identityDecisions).reviewProviderEvidence(2L, 1L, attemptId, "APPROVE", null);
        assertThrows(IdentityAiException.class, () -> service.review(2L, 1L, 3, "APPROVE", null));
        assertEquals(UserStatus.PENDING, user.getStatus());
        assertEquals(SUBMITTED, application.getStatus());
        verifyNoInteractions(providers, reviews);
    }

    @Test
    void rejectsPartialBankAndInvalidCoordinates() {
        var partial = new ProviderApplicationRequest(3L, "Test", "0900000000", "Address",
                BigDecimal.TEN, BigDecimal.TEN, "VCB", null, null);
        assertThrows(IdentityAiException.class, () -> service.save(1L, partial));
        var invalid = new ProviderApplicationRequest(3L, "Test", "0900000000", "Address",
                new BigDecimal("91"), BigDecimal.TEN, null, null, null);
        assertThrows(IdentityAiException.class, () -> service.save(1L, invalid));
    }
}
