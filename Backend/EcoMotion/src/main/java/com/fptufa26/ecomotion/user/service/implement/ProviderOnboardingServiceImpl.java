package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.user.dto.request.ProviderApplicationRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderApplicationResponse;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.repository.*;
import com.fptufa26.ecomotion.user.service.ProviderOnboardingService;
import com.fptufa26.ecomotion.finance.enums.SupportedBank;
import com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus;
import com.fptufa26.ecomotion.verification.identity.enums.IdentityVerificationMethod;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

import static com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus.*;

@Service
@Transactional
public class ProviderOnboardingServiceImpl implements ProviderOnboardingService {
    private final UserRepository users;
    private final ProviderRepository providers;
    private final ProviderApplicationRepository applications;
    private final ProviderApplicationReviewRepository reviews;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final Clock clock;
    private final com.fptufa26.ecomotion.verification.identity.service.IdentityDecisionService identityDecisions;
    private final BigDecimal commissionRate;
    private final BigDecimal depositRatio;

    public ProviderOnboardingServiceImpl(UserRepository users, ProviderRepository providers,
            ProviderApplicationRepository applications, ProviderApplicationReviewRepository reviews,
            ObjectMapper mapper, Validator validator, Clock clock,
            com.fptufa26.ecomotion.verification.identity.service.IdentityDecisionService identityDecisions,
            @Value("${app.provider.default-commission-rate:0.10}") BigDecimal commissionRate,
            @Value("${app.provider.default-deposit-ratio:0.30}") BigDecimal depositRatio) {
        this.users = users;
        this.providers = providers;
        this.applications = applications;
        this.reviews = reviews;
        this.mapper = mapper;
        this.validator = validator;
        this.clock = clock;
        this.identityDecisions = identityDecisions;
        this.commissionRate = commissionRate;
        this.depositRatio = depositRatio;
        if (commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0
                || depositRatio.signum() < 0 || depositRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Invalid provider default rates");
        }
    }

    @Override
    public ProviderApplicationResponse get(Long id) {
        User user = users.findById(id).orElseThrow(() -> invalid("PROVIDER_ACCOUNT_NOT_FOUND"));
        if (user.getRole() != Role.PROVIDER || (user.getStatus() != UserStatus.PENDING && !user.isActive())) {
            throw new AccessDeniedException("Provider onboarding is unavailable");
        }
        return view(user, applications.findById(id).orElseGet(() -> draft(id)));
    }

    @Override
    public ProviderApplicationResponse save(Long id, ProviderApplicationRequest request) {
        User user = owner(id);
        var application = applications.findById(id).orElseGet(() -> draft(id));
        requireVersion(application, request.version());
        if (!editable(application) || user.getStatus() != UserStatus.PENDING) throw conflict("PROVIDER_APPLICATION_NOT_EDITABLE");
        validate(request);
        application.setProfileJson(mapper.writeValueAsString(request));
        application.setStatus(DRAFT);
        application.setReasonCode(null);
        return view(user, applications.saveAndFlush(application));
    }

    @Override
    public ProviderApplicationResponse submit(Long id, long version) {
        User user = owner(id);
        var application = applications.findById(id).orElseThrow(() -> conflict("PROVIDER_PROFILE_REQUIRED"));
        if (application.getStatus() == SUBMITTED || application.getStatus() == APPROVED) return view(user, application);
        requireVersion(application, version);
        if (!editable(application) || user.getStatus() != UserStatus.PENDING) throw conflict("PROVIDER_APPLICATION_NOT_EDITABLE");
        validate(profile(application));
        requireAccountEvidence(user);
        application.setSubmittedAttemptId(verified(user) ? null : identityDecisions.submitProviderEvidence(id));
        application.setStatus(SUBMITTED);
        application.setSubmittedAt(clock.instant());
        application.setSubmittedIdentityId(verified(user) ? user.getIdentity().getId() : null);
        application.setReasonCode(null);
        return view(user, applications.saveAndFlush(application));
    }

    @Override
    public ProviderApplicationResponse adminGet(Long adminId, Long id) {
        admin(adminId);
        return get(id);
    }

    @Override
    public List<ProviderApplicationResponse> pending(Long adminId, int page, ProviderApplicationStatus status) {
        admin(adminId);
        if (page < 0) throw invalid("PROVIDER_PAGE_INVALID");
        return applications.findByStatus(status, PageRequest.of(page, 20,
                Sort.by("submittedAt").ascending().and(Sort.by("userId"))))
                .map(application -> view(users.findById(application.getUserId()).orElseThrow(), application)).getContent();
    }

    @Override
    public ProviderApplicationResponse review(Long adminId, Long id, long version, String decision, String reason) {
        admin(adminId);
        User user = owner(id);
        var application = applications.findById(id).orElseThrow(() -> conflict("PROVIDER_PROFILE_REQUIRED"));
        requireVersion(application, version);
        boolean reopen = "REOPEN".equals(decision) && application.getStatus() == REJECTED;
        if (application.getStatus() != SUBMITTED && !reopen) throw conflict("PROVIDER_REVIEW_STATE_INVALID");
        if (decision == null || !Set.of("APPROVE", "NEEDS_CHANGES", "REJECT", "REOPEN").contains(decision)
                || ("REOPEN".equals(decision) && !reopen)) {
            throw invalid("PROVIDER_REVIEW_DECISION_INVALID");
        }
        if (!"APPROVE".equals(decision) && (reason == null || !reason.matches("[A-Z][A-Z0-9_]{2,99}"))) {
            throw invalid("PROVIDER_REVIEW_REASON_REQUIRED");
        }
        if ("APPROVE".equals(decision)) {
            requireAccountEvidence(user);
            if (application.getSubmittedAttemptId() != null) {
                identityDecisions.reviewProviderEvidence(adminId, id, application.getSubmittedAttemptId(), decision, reason);
                application.setSubmittedIdentityId(user.getIdentity().getId());
            }
            if (!verified(user) || !Objects.equals(application.getSubmittedIdentityId(), user.getIdentity().getId())) {
                throw conflict("PROVIDER_IDENTITY_APPROVAL_REQUIRED");
            }
            var data = profile(application);
            validate(data);
            if (providers.existsByUser_Id(id)) throw conflict("PROVIDER_PROFILE_ALREADY_EXISTS");
            var provider = new Provider();
            provider.setUser(user);
            provider.setProviderType(ProviderType.INDIVIDUAL);
            provider.setDisplayName(data.displayName().trim());
            provider.setHotline(data.hotline().trim());
            provider.setDepotAddress(data.depotAddress().trim());
            provider.setDepotLatitude(data.latitude());
            provider.setDepotLongitude(data.longitude());
            provider.setCommissionRate(commissionRate);
            provider.setDepositRatio(depositRatio);
            if (present(data.bankCode())) {
                SupportedBank bank = bank(data.bankCode());
                provider.setBankCode(bank.getCode());
                provider.setBankName(bank.getShortName());
                provider.setBankAccountNo(data.bankAccountNo());
                provider.setBankAccountHolder(data.bankAccountHolder().trim());
            }
            provider.setStatus(ProviderStatus.APPROVED);
            providers.saveAndFlush(provider);
            user.setStatus(UserStatus.ACTIVE);
            users.saveAndFlush(user);
            application.setStatus(APPROVED);
        } else {
            if (!reopen && application.getSubmittedAttemptId() != null) {
                identityDecisions.reviewProviderEvidence(adminId, id, application.getSubmittedAttemptId(), decision, reason);
            }
            application.setStatus("REJECT".equals(decision) ? REJECTED : NEEDS_CHANGES);
        }
        application.setReviewedBy(adminId);
        application.setReviewedAt(clock.instant());
        application.setReasonCode("APPROVE".equals(decision) ? null : reason);
        var review = new ProviderApplicationReview();
        review.setUserId(id);
        review.setApplicationVersion(version);
        review.setAdminId(adminId);
        review.setDecision(decision);
        review.setReasonCode(application.getReasonCode());
        review.setReviewedAt(clock.instant());
        review.setProfileSnapshotJson(application.getProfileJson());
        review.setSubmittedAttemptId(application.getSubmittedAttemptId());
        reviews.save(review);
        return view(user, applications.saveAndFlush(application));
    }

    @Override
    public List<ProviderApplicationReview> history(Long adminId, Long id) {
        admin(adminId);
        return reviews.findByUserIdOrderByIdAsc(id);
    }

    private User owner(Long id) {
        var user = users.findForUpdate(id).orElseThrow(() -> invalid("PROVIDER_ACCOUNT_NOT_FOUND"));
        if (user.getRole() != Role.PROVIDER || (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING)) {
            throw new AccessDeniedException("Provider onboarding is unavailable");
        }
        return user;
    }

    private void admin(Long id) {
        var user = users.findById(id).orElseThrow(() -> new AccessDeniedException("Admin required"));
        if (user.getRole() != Role.ADMIN || !user.isActive()) throw new AccessDeniedException("Admin required");
    }

    private void requireAccountEvidence(User user) {
        if (user.getEmailVerifiedAt() == null || user.getTermsAcceptedAt() == null
                || user.getPhoneVerifiedAt() == null || !present(user.getPhone())) {
            throw conflict("PROVIDER_ACCOUNT_VERIFICATION_REQUIRED");
        }
    }

    private boolean verified(User user) {
        return user.getIdentity() != null && user.getIdentity().getStatus() == PersonIdentityStatus.VERIFIED
                && user.getIdentityReviewedAt() != null;
    }

    private boolean editable(ProviderApplication application) {
        return application.getStatus() == DRAFT || application.getStatus() == NEEDS_CHANGES || application.getStatus() == REJECTED;
    }

    private ProviderApplication draft(Long id) {
        var application = new ProviderApplication();
        application.setUserId(id);
        return application;
    }

    private void requireVersion(ProviderApplication application, Long expected) {
        long current = application.getVersion() == null ? 0 : application.getVersion();
        if (expected == null || expected != current) throw conflict("PROVIDER_APPLICATION_STALE");
    }

    private ProviderApplicationRequest profile(ProviderApplication application) {
        return application.getProfileJson() == null ? null : mapper.readValue(application.getProfileJson(), ProviderApplicationRequest.class);
    }

    private void validate(ProviderApplicationRequest data) {
        if (data == null || !validator.validate(data).isEmpty()) throw invalid("PROVIDER_PROFILE_INVALID");
        boolean anyBank = present(data.bankCode()) || present(data.bankAccountNo()) || present(data.bankAccountHolder());
        if (anyBank && (!present(data.bankCode()) || !present(data.bankAccountNo()) || !present(data.bankAccountHolder()))) {
            throw invalid("PROVIDER_BANK_FIELDS_REQUIRED");
        }
        if (anyBank) {
            var selected = bank(data.bankCode());
            int length = data.bankAccountNo().length();
            if (length < selected.getMinAccountLength() || length > selected.getMaxAccountLength()) {
                throw invalid("PROVIDER_BANK_ACCOUNT_INVALID");
            }
        }
    }

    private SupportedBank bank(String code) {
        return Arrays.stream(SupportedBank.values()).filter(value -> value.getCode().equals(code)).findFirst()
                .orElseThrow(() -> invalid("PROVIDER_BANK_CODE_INVALID"));
    }

    private ProviderApplicationResponse view(User user, ProviderApplication application) {
        boolean canEdit = editable(application) && user.getStatus() == UserStatus.PENDING;
        long version = application.getVersion() == null ? 0 : application.getVersion();
        var data = profile(application);
        var currentProfile = data == null ? null : new ProviderApplicationRequest(version, data.displayName(),
                data.hotline(), data.depotAddress(), data.latitude(), data.longitude(), data.bankCode(),
                data.bankAccountNo(), data.bankAccountHolder());
        String next = switch (application.getStatus()) {
            case APPROVED -> "LOGIN_OR_REFRESH";
            case SUBMITTED -> "WAIT_FOR_ADMIN";
            case REJECTED -> "EDIT_AND_RESUBMIT";
            default -> !verified(user) && !identityDecisions.providerEvidenceReady(user.getId()) ? "VERIFY_IDENTITY" : application.getProfileJson() == null
                    ? "COMPLETE_PROVIDER_PROFILE" : "SUBMIT_PROVIDER_APPLICATION";
        };
        return new ProviderApplicationResponse(user.getId(), version,
                application.getStatus(), currentProfile, application.getSubmittedAt(), application.getReviewedAt(),
                application.getReviewedBy(), application.getReasonCode(), verified(user), canEdit,
                canEdit && (verified(user) || identityDecisions.providerEvidenceReady(user.getId())) && user.getPhoneVerifiedAt() != null && application.getProfileJson() != null,
                next, user.getEmail(), user.getFullName(), application.getSubmittedAttemptId());
    }

    private boolean present(String value) { return value != null && !value.isBlank(); }
    private IdentityAiException conflict(String code) { return new IdentityAiException(HttpStatus.CONFLICT, code, code); }
    private IdentityAiException invalid(String code) { return new IdentityAiException(HttpStatus.BAD_REQUEST, code, code); }
}
