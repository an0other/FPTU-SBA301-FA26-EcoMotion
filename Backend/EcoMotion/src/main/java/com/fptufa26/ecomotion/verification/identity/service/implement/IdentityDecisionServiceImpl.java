package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.dto.response.*;
import com.fptufa26.ecomotion.verification.identity.entity.*;
import com.fptufa26.ecomotion.verification.identity.enums.*;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.repository.*;
import com.fptufa26.ecomotion.verification.identity.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus.*;

@Service
@Transactional
public class IdentityDecisionServiceImpl implements IdentityDecisionService {
    private final IdentityVerificationAttemptRepository attempts;
    private final PersonIdentityRepository identities;
    private final UserService users;
    private final IdentityFingerprintService fingerprints;
    private final IdentityVerificationAttemptService sessions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private static final int MINIMUM_FRAMES = 4;
    private static final double MINIMUM_COVERAGE = 0.75;
    private final String policyVersion;
    private final Double acceptanceThreshold;

    public IdentityDecisionServiceImpl(IdentityVerificationAttemptRepository attempts,
            PersonIdentityRepository identities, UserService users, IdentityFingerprintService fingerprints,
            IdentityVerificationAttemptService sessions, ObjectMapper mapper, Clock clock,
            @Value("${app.identity.acceptance-threshold:}") String threshold) {
        this.attempts = attempts;
        this.identities = identities;
        this.users = users;
        this.fingerprints = fingerprints;
        this.sessions = sessions;
        this.mapper = mapper;
        this.clock = clock;
        acceptanceThreshold = threshold == null || threshold.isBlank() ? null : Double.valueOf(threshold.trim());
        if (acceptanceThreshold != null && (!Double.isFinite(acceptanceThreshold)
                || acceptanceThreshold <= 0 || acceptanceThreshold > 1)) {
            throw new IllegalArgumentException("Identity acceptance threshold must be in (0, 1]");
        }
        policyVersion = "identity-screen-v1:t=" + acceptanceThreshold + ":n=" + MINIMUM_FRAMES + ":c=" + MINIMUM_COVERAGE;
    }

    @Override
    public IdentityDecisionResponse submit(Long userId, UUID id, long version, boolean confirmed) {
        return assessSubmission(userId, id, version, confirmed, false);
    }

    @Override
    public boolean providerEvidenceReady(Long userId) {
        return attempts.findFirstByUser_IdOrderByIdDesc(userId)
                .filter(a -> a.getRole() == Role.PROVIDER && (a.getStatus() == SUBMITTED
                        || a.getStatus() == READY_TO_SUBMIT && a.getExpiresAt().isAfter(clock.instant())))
                .isPresent();
    }

    @Override
    public UUID submitProviderEvidence(Long userId) {
        var candidate = attempts.findFirstByUser_IdOrderByIdDesc(userId).orElseThrow(this::notFound);
        if (candidate.getRole() != Role.PROVIDER) throw failure(HttpStatus.CONFLICT, "IDENTITY_ATTEMPT_STATE_INVALID");
        var result = assessSubmission(userId, candidate.getPublicId(), candidate.getVersion(), true, true);
        if (result.status() != SUBMITTED) throw failure(HttpStatus.CONFLICT, "IDENTITY_FACE_RETRY_REQUIRED");
        return candidate.getPublicId();
    }

    @Override
    public void reviewProviderEvidence(Long adminId, Long userId, UUID id, String decision, String reason) {
        var attempt = attempts.findOwnedForUpdate(id, userId).orElseThrow(this::notFound);
        review(adminId, id, attempt.getVersion(), decision, reason);
        if ("NEEDS_CHANGES".equals(decision)) {
            attempt.setStatus(READY_TO_SUBMIT);
            attempts.saveAndFlush(attempt);
        }
    }

    private IdentityDecisionResponse assessSubmission(Long userId, UUID id, long version, boolean confirmed, boolean queueProvider) {
        var attempt = attempts.findOwnedForUpdate(id, userId).orElseThrow(this::notFound);
        User user = users.lockForIdentityDecision(userId);
        allowed(user, attempt);
        if (!confirmed) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_CONFIRMATION_REQUIRED");
        }
        if (Set.of(SUBMITTED, APPROVED, REJECTED).contains(attempt.getStatus())) {
            return view(attempt);
        }
        requireVersion(attempt, version);
        if (attempt.getStatus() != READY_TO_SUBMIT || !attempt.getExpiresAt().isAfter(clock.instant())) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_ATTEMPT_STATE_INVALID");
        }
        if (user.getIdentity() != null && (user.getRole() != Role.PROVIDER || user.getIdentityReviewedAt() != null)) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_ALREADY_BOUND");
        }
        if (user.getPhone() == null || user.getPhone().isBlank() || user.getPhoneVerifiedAt() == null) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_PHONE_VERIFICATION_REQUIRED");
        }
        var document = document(attempt);
        var video = attempt.getVideoJson() == null ? null : mapper.readValue(attempt.getVideoJson(), IdentityVideoResult.class);
        if (attempt.getFrontKey() == null || attempt.getBackKey() == null || attempt.getVideoKey() == null
                || document == null || !document.requiredFieldsReadable() || video == null
                || video.workflow() == null || !"READY_FOR_REVIEW".equals(video.workflow().status())
                || video.motionChallenge() == null || !"PASS".equals(video.motionChallenge().status())) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_EVIDENCE_INCOMPLETE");
        }
        if (acceptanceThreshold == null) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_THRESHOLD_NOT_CONFIGURED");
        }
        attempt.setSubmittedAt(clock.instant());
        var scores = video.faceMatching();
        boolean accepted = scores != null && scores.scoredFrames() != null
                && scores.candidateFrames() != null && scores.candidateFrames() > 0
                && scores.scoredFrames() >= MINIMUM_FRAMES
                && scores.scoredFrames() <= scores.candidateFrames() && scores.median() != null
                && Double.isFinite(scores.median()) && scores.median() <= 1
                && scores.median() >= acceptanceThreshold
                && (double) scores.scoredFrames() / scores.candidateFrames() >= MINIMUM_COVERAGE;
        attempt.setDecisionPolicyVersion(policyVersion);
        if (!accepted) {
            attempt.setDecisionActor("SYSTEM");
            attempt.setDecidedAt(clock.instant());
            attempt.setStatus(NEEDS_CHANGES);
            attempt.setDecisionReason("IDENTITY_FACE_RETRY_REQUIRED");
        } else if (user.getRole() == Role.PROVIDER) {
            attempt.setStatus(queueProvider ? SUBMITTED : READY_TO_SUBMIT);
            attempt.setDecisionReason(null);
        } else {
            attempt.setDecisionActor("SYSTEM");
            attempt.setDecidedAt(clock.instant());
            approve(attempt, document, null);
        }
        return view(attempts.saveAndFlush(attempt));
    }

    @Override
    public IdentityDecisionResponse review(Long adminId, UUID id, long version, String decision, String reason) {
        admin(adminId);
        var attempt = attempts.findPublicForUpdate(id).orElseThrow(this::notFound);
        requireVersion(attempt, version);
        boolean reopen = "REOPEN".equals(decision) && attempt.getStatus() == REJECTED;
        if (attempt.getRole() != Role.PROVIDER || attempt.getStatus() != SUBMITTED && !reopen) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_REVIEW_STATE_INVALID");
        }
        var user = users.lockForIdentityDecision(attempt.getUser().getId());
        allowed(user, attempt);
        if (!Set.of("APPROVE", "NEEDS_CHANGES", "REJECT", "REOPEN").contains(decision)
                || "REOPEN".equals(decision) && !reopen) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_REVIEW_DECISION_INVALID");
        }
        if (!"APPROVE".equals(decision) && (reason == null || !reason.matches("[A-Z][A-Z0-9_]{2,99}"))) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_REVIEW_REASON_REQUIRED");
        }
        attempt.setDecisionActor("ADMIN");
        attempt.setReviewedBy(adminId);
        attempt.setDecidedAt(clock.instant());
        attempt.setDecisionPolicyVersion("provider-admin-v1");
        attempt.setDecisionReason("APPROVE".equals(decision) ? null : reason);
        if ("APPROVE".equals(decision)) {
            approve(attempt, document(attempt), adminId);
        } else if ("REJECT".equals(decision)) {
            attempt.setStatus(REJECTED);
            attempt.setActiveUserId(null);
        } else {
            if (reopen && attempts.existsByActiveUserId(user.getId())) {
                throw failure(HttpStatus.CONFLICT, "IDENTITY_CANCEL_ACTIVE_ATTEMPT_FIRST");
            }
            attempt.setStatus(NEEDS_CHANGES);
            attempt.setActiveUserId(user.getId());
            attempt.setExpiresAt(clock.instant().plus(Duration.ofHours(24)));
        }
        return view(attempts.saveAndFlush(attempt));
    }

    private void approve(IdentityVerificationAttempt attempt, IdentityAiAssessmentResponse.DocumentRead doc, Long adminId) {
        if (doc == null || !doc.requiredFieldsReadable() || doc.fullName() == null
                || doc.fullName().isBlank() || doc.fullName().length() > 150
                || (doc.address() != null && doc.address().length() > 500)
                || (doc.sex() != null && doc.sex().length() > 10)) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_DOCUMENT_INVALID");
        }
        String number = fingerprints.normalizeCccdNumber(doc.documentNumber());
        String fingerprint = fingerprints.fingerprint(number);
        var birth = date(doc.dateOfBirth(), true);
        var expiry = date(doc.expiryDate(), false);
        if (birth.isAfter(LocalDate.now(clock)) || expiry != null && expiry.isBefore(LocalDate.now(clock))) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_DOCUMENT_DATE_INVALID");
        }
        var owner = users.findById(attempt.getUser().getId()).orElseThrow();
        if (owner.getIdentity() != null) {
            if (adminId == null || owner.getRole() != Role.PROVIDER
                    || !fingerprint.equals(owner.getIdentity().getCccdFingerprint())
                    || !birth.equals(owner.getIdentity().getDateOfBirth())) {
                throw failure(HttpStatus.CONFLICT, "IDENTITY_LINK_DOCUMENT_MISMATCH");
            }
            users.markProviderIdentityReviewed(owner.getId());
            attempt.setResultingIdentityId(owner.getIdentity().getId());
            attempt.setStatus(APPROVED);
            attempt.setDecisionReason(null);
            attempt.setActiveUserId(null);
            return;
        }
        if (identities.findByCccdFingerprint(fingerprint).isPresent()) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_LINK_REQUIRED");
        }
        var identity = new PersonIdentity();
        identity.setCccdNo(number);
        identity.setCccdFingerprint(fingerprint);
        identity.setFingerprintKeyVersion(fingerprints.keyVersion());
        identity.setFullName(doc.fullName().trim());
        identity.setDateOfBirth(date(doc.dateOfBirth(), true));
        identity.setDocumentExpiry(date(doc.expiryDate(), false));
        if (identity.getDateOfBirth().isAfter(LocalDate.now(clock))
                || (identity.getDocumentExpiry() != null && identity.getDocumentExpiry().isBefore(LocalDate.now(clock)))) {
            throw failure(HttpStatus.CONFLICT, "IDENTITY_DOCUMENT_DATE_INVALID");
        }
        identity.setGender(doc.sex());
        identity.setAddress(doc.address());
        identity.setStatus(PersonIdentityStatus.VERIFIED);
        identity.setVerificationMethod(adminId == null ? IdentityVerificationMethod.AUTOMATED_POLICY : IdentityVerificationMethod.ADMIN_REVIEW);
        identity.setPolicyVersion(attempt.getDecisionPolicyVersion());
        identity.setApprovedAttemptId(attempt.getId());
        identity.setVerifiedAt(clock.instant());
        identity.setReviewedBy(adminId);
        identity.setReviewedAt(adminId == null ? null : clock.instant());
        identity = identities.saveAndFlush(identity);
        users.applyVerifiedIdentity(attempt.getUser().getId(), identity);
        attempt.setResultingIdentityId(identity.getId());
        attempt.setStatus(APPROVED);
        attempt.setDecisionReason(null);
        attempt.setActiveUserId(null);
    }

    private LocalDate date(String value, boolean required) {
        if (value == null || value.isBlank()) {
            if (!required) return null;
            throw failure(HttpStatus.CONFLICT, "IDENTITY_DOCUMENT_DATE_INVALID");
        }
        for (var format : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))) {
            try { return LocalDate.parse(value, format); } catch (DateTimeParseException ignored) { }
        }
        throw failure(HttpStatus.CONFLICT, "IDENTITY_DOCUMENT_DATE_INVALID");
    }

    @Override
    public List<IdentityDecisionResponse> pending(Long adminId, int page, IdentityAttemptStatus status) {
        admin(adminId);
        if (page < 0) throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_PAGE_INVALID");
        if (!Set.of(SUBMITTED, APPROVED, REJECTED, NEEDS_CHANGES).contains(status)) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_REVIEW_STATUS_INVALID");
        }
        return attempts.findByStatusAndRole(status, Role.PROVIDER,
                PageRequest.of(page, 20, Sort.by("submittedAt").ascending().and(Sort.by("id")))).map(this::view).getContent();
    }

    @Override
    public IdentityAttemptResponse detail(Long adminId, UUID id) {
        admin(adminId);
        var attempt = providerAttempt(id);
        return sessions.get(attempt.getUser().getId(), id);
    }

    @Override
    public byte[] evidence(Long adminId, UUID id, String kind) {
        admin(adminId);
        var attempt = providerAttempt(id);
        return sessions.evidence(attempt.getUser().getId(), id, kind);
    }

    private IdentityVerificationAttempt providerAttempt(UUID id) {
        var attempt = attempts.findPublicForUpdate(id).orElseThrow(this::notFound);
        if (attempt.getRole() != Role.PROVIDER || attempt.getSubmittedAt() == null) throw notFound();
        return attempt;
    }

    private void admin(Long id) {
        var user = users.findById(id).orElseThrow(() -> new AccessDeniedException("Admin required"));
        if (user.getRole() != Role.ADMIN || user.getStatus() != UserStatus.ACTIVE) throw new AccessDeniedException("Admin required");
    }

    private void allowed(User user, IdentityVerificationAttempt attempt) {
        if ((user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING)
                || user.getRole() != attempt.getRole() || (user.getRole() != Role.RENTER && user.getRole() != Role.PROVIDER)) {
            throw new AccessDeniedException("Account cannot submit identity");
        }
    }

    private IdentityAiAssessmentResponse.DocumentRead document(IdentityVerificationAttempt attempt) {
        return attempt.getDocumentJson() == null ? null : mapper.readValue(attempt.getDocumentJson(), IdentityAiAssessmentResponse.DocumentRead.class);
    }

    private void requireVersion(IdentityVerificationAttempt attempt, long version) {
        if (!Objects.equals(attempt.getVersion(), version)) throw failure(HttpStatus.CONFLICT, "IDENTITY_ATTEMPT_STALE");
    }

    private IdentityDecisionResponse view(IdentityVerificationAttempt attempt) {
        return new IdentityDecisionResponse(attempt.getPublicId(), attempt.getVersion(), attempt.getStatus(),
                attempt.getDecisionActor(), attempt.getDecisionReason(), attempt.getDecisionPolicyVersion(),
                attempt.getSubmittedAt(), attempt.getDecidedAt(), attempt.getResultingIdentityId(),
                attempt.getStatus() == APPROVED && attempt.getRole() == Role.RENTER,
                attempt.getUser().getId(), attempt.getUser().getEmail(), attempt.getUser().getFullName());
    }

    private IdentityAiException notFound() { return failure(HttpStatus.NOT_FOUND, "IDENTITY_ATTEMPT_NOT_FOUND"); }
    private IdentityAiException failure(HttpStatus status, String code) { return new IdentityAiException(status, code, code); }
}
