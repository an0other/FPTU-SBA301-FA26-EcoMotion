package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityLinkResponse;
import com.fptufa26.ecomotion.verification.identity.entity.*;
import com.fptufa26.ecomotion.verification.identity.enums.*;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.repository.*;
import com.fptufa26.ecomotion.verification.identity.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

@Service
@RequiredArgsConstructor
@Transactional
public class IdentityAccountLinkServiceImpl implements IdentityAccountLinkService {
    private final IdentityAccountLinkRepository links;
    private final PersonIdentityRepository identities;
    private final IdentityVerificationAttemptRepository attempts;
    private final IdentityEvidenceStorageService storage;
    private final UserService users;
    private final PasswordEncoder passwords;
    private final Clock clock;
    @Value("${app.identity.link-ttl:15m}") private Duration lifetime;
    @Value("${app.identity.ai.max-image-bytes:8388608}") private int maxImageBytes;
    @Value("${app.identity.ai.max-video-bytes:20971520}") private int maxVideoBytes;

    @Override
    public IdentityLinkResponse request(Long sourceId, String password, String email) {
        var target = users.findByEmail(email.trim()).orElseThrow(() -> failure("IDENTITY_LINK_TARGET_INVALID"));
        var pair = lockPair(sourceId, target.getId());
        var source = pair.get(sourceId);
        target = pair.get(target.getId());
        eligible(source, target);
        if (!passwords.matches(password, source.getPasswordHash())) throw new AccessDeniedException("Password confirmation failed");
        var link = new IdentityAccountLink();
        link.setId(UUID.randomUUID());
        link.setSourceUserId(sourceId);
        link.setTargetUserId(target.getId());
        link.setIdentityId(source.getIdentity().getId());
        link.setSourceCredentialStamp(stamp(source.getPasswordHash()));
        link.setStatus("PENDING");
        link.setExpiresAt(clock.instant().plus(lifetime));
        return view(links.saveAndFlush(link));
    }

    @Override
    public List<IdentityLinkResponse> list(Long userId, int page) {
        requireUser(userId);
        if (page < 0) throw failure("IDENTITY_PAGE_INVALID");
        return links.findVisible(userId, PageRequest.of(page, 20)).stream().map(this::view).toList();
    }

    @Override
    public IdentityLinkResponse confirm(Long targetId, UUID id, long version, String password) {
        var initial = links.findById(id).orElseThrow(() -> failure("IDENTITY_LINK_NOT_FOUND"));
        if (!initial.getTargetUserId().equals(targetId)) throw new AccessDeniedException("Target account required");
        var pair = lockPair(initial.getSourceUserId(), targetId);
        var link = links.findForUpdate(id).orElseThrow(() -> failure("IDENTITY_LINK_NOT_FOUND"));
        var source = pair.get(link.getSourceUserId());
        var target = pair.get(targetId);
        if (!passwords.matches(password, target.getPasswordHash())) throw new AccessDeniedException("Password confirmation failed");
        if ("CONFIRMED".equals(link.getStatus())) return view(link);
        requirePending(link, version);
        eligible(source, target);
        var identity = identities.findByIdForUpdate(link.getIdentityId()).orElseThrow(() -> failure("IDENTITY_LINK_NOT_ALLOWED"));
        if (!source.getIdentity().getId().equals(identity.getId()) || identity.getStatus() != PersonIdentityStatus.VERIFIED
                || !stamp(source.getPasswordHash()).equals(link.getSourceCredentialStamp())) throw failure("IDENTITY_LINK_NOT_ALLOWED");
        if (attempts.existsByActiveUserId(targetId)) throw failure("IDENTITY_CANCEL_ACTIVE_ATTEMPT_FIRST");
        users.attachLinkedIdentity(targetId, identity.getId());
        if (target.getRole() == Role.PROVIDER) reuseEvidence(target, identity);
        link.setStatus("CONFIRMED");
        link.setConfirmedAt(clock.instant());
        return view(links.saveAndFlush(link));
    }

    @Override
    public IdentityLinkResponse cancel(Long userId, UUID id, long version) {
        requireUser(userId);
        var link = links.findForUpdate(id).orElseThrow(() -> failure("IDENTITY_LINK_NOT_FOUND"));
        if (!link.getSourceUserId().equals(userId) && !link.getTargetUserId().equals(userId)) throw new AccessDeniedException("Account mismatch");
        requirePending(link, version);
        link.setStatus("CANCELLED");
        return view(links.saveAndFlush(link));
    }

    private Map<Long, User> lockPair(Long source, Long target) {
        if (source.equals(target)) throw failure("IDENTITY_LINK_TARGET_INVALID");
        var pair = new HashMap<Long, User>();
        java.util.stream.Stream.of(source, target).sorted().forEach(id -> pair.put(id, users.lockForIdentityDecision(id)));
        return pair;
    }

    private void eligible(User source, User target) {
        if (!Set.of(Role.RENTER, Role.PROVIDER).contains(source.getRole()) || source.getRole() == target.getRole()
                || !Set.of(Role.RENTER, Role.PROVIDER).contains(target.getRole()) || !source.isActive()
                || source.getIdentity() == null || source.getIdentity().getStatus() != PersonIdentityStatus.VERIFIED
                || source.getIdentity().getVerificationMethod() == IdentityVerificationMethod.DEMO_SEED
                || target.getStatus() != UserStatus.PENDING || target.getIdentity() != null
                || target.getEmailVerifiedAt() == null || target.getTermsAcceptedAt() == null
                || source.getIdentity().getDocumentExpiry() != null && source.getIdentity().getDocumentExpiry().isBefore(LocalDate.now(clock))
                || source.getRole() == Role.PROVIDER && !users.isProviderOperational(source.getId())) {
            throw failure("IDENTITY_LINK_NOT_ALLOWED");
        }
    }

    private void requirePending(IdentityAccountLink link, long version) {
        if (!Objects.equals(link.getVersion(), version) || !"PENDING".equals(link.getStatus())
                || !link.getExpiresAt().isAfter(clock.instant())) throw failure("IDENTITY_LINK_STALE_OR_EXPIRED");
    }

    private void requireUser(Long id) {
        var user = users.findById(id).orElseThrow(() -> new AccessDeniedException("Account not found"));
        if (!Set.of(Role.RENTER, Role.PROVIDER).contains(user.getRole()) || user.getStatus() == UserStatus.LOCKED) {
            throw new AccessDeniedException("Account unavailable");
        }
    }

    private void reuseEvidence(User target, PersonIdentity identity) {
        var source = identity.getApprovedAttemptId() == null ? null : attempts.findById(identity.getApprovedAttemptId()).orElse(null);
        if (source == null || source.getStatus() != IdentityAttemptStatus.APPROVED
                || !Objects.equals(source.getResultingIdentityId(), identity.getId()) || source.getFrontKey() == null
                || source.getBackKey() == null || source.getVideoKey() == null) return;
        var copy = new IdentityVerificationAttempt();
        copy.setPublicId(UUID.randomUUID());
        var stored = new ArrayList<String>();
        try {
            for (String key : List.of(source.getFrontKey(), source.getBackKey(), source.getVideoKey())) {
                int limit = key.equals(source.getVideoKey()) ? maxVideoBytes : maxImageBytes;
                byte[] content = storage.read(key, limit);
                stored.add(storage.store(copy.getPublicId(), new EvidenceFile(content), limit));
            }
        } catch (RuntimeException error) {
            stored.forEach(storage::delete);
            if (error instanceof IdentityAiException failure && failure.getStatus().value() == 404) return;
            throw error;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) stored.forEach(storage::delete);
                    }
                });
        copy.setUser(target);
        copy.setRole(Role.PROVIDER);
        copy.setActiveUserId(target.getId());
        copy.setStatus(IdentityAttemptStatus.READY_TO_SUBMIT);
        copy.setExpiresAt(clock.instant().plus(Duration.ofHours(24)));
        copy.setDocumentRevision(1);
        copy.setFrontKey(stored.get(0));
        copy.setBackKey(stored.get(1));
        copy.setVideoKey(stored.get(2));
        copy.setFrontSha256(source.getFrontSha256());
        copy.setBackSha256(source.getBackSha256());
        copy.setDocumentJson(source.getDocumentJson());
        copy.setVideoJson(source.getVideoJson());
        copy.setDecisionPolicyVersion("linked-evidence-v1");
        attempts.saveAndFlush(copy);
    }

    private String stamp(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private IdentityLinkResponse view(IdentityAccountLink link) {
        var target = users.findById(link.getTargetUserId()).orElseThrow();
        String next = !"CONFIRMED".equals(link.getStatus()) ? "PENDING".equals(link.getStatus())
                && link.getExpiresAt().isAfter(clock.instant()) ? "CONFIRM_LINK" : "CREATE_NEW_LINK"
                : target.isActive() ? "LOGIN_OR_DASHBOARD" : target.getPhoneVerifiedAt() == null ? "VERIFY_PHONE"
                : target.getRole() == Role.PROVIDER ? "PROVIDER_IDENTITY_REVIEW" : "LOGIN_OR_DASHBOARD";
        return new IdentityLinkResponse(link.getId(), link.getVersion(), link.getSourceUserId(),
                users.findById(link.getSourceUserId()).orElseThrow().getEmail(), link.getTargetUserId(),
                target.getEmail(),
                "PENDING".equals(link.getStatus()) && !link.getExpiresAt().isAfter(clock.instant()) ? "EXPIRED" : link.getStatus(),
                link.getExpiresAt(), link.getConfirmedAt(), target.getStatus(), next);
    }

    private IdentityAiException failure(String code) { return new IdentityAiException(HttpStatus.CONFLICT, code, code); }

    private record EvidenceFile(byte[] content) implements org.springframework.web.multipart.MultipartFile {
        public String getName() { return "evidence"; }
        public String getOriginalFilename() { return "evidence.bin"; }
        public String getContentType() { return "application/octet-stream"; }
        public boolean isEmpty() { return content.length == 0; }
        public long getSize() { return content.length; }
        public byte[] getBytes() { return content; }
        public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        public void transferTo(java.io.File destination) throws java.io.IOException {
            java.nio.file.Files.write(destination.toPath(), content);
        }
    }
}
