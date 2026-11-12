package com.fptufa26.ecomotion.verification.identity.controller;

import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAttemptResponse;
import com.fptufa26.ecomotion.verification.identity.service.IdentityVerificationAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class IdentityVerificationAttemptController {

    private final IdentityVerificationAttemptService attempts;
    private final TokenService tokens;
    private final UserService users;
    private final com.fptufa26.ecomotion.verification.identity.service.IdentityDecisionService decisions;

    @PostMapping("/api/identity/attempts/{id}/submit")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityDecisionResponse submit(
            @AuthenticationPrincipal SecurityUser user, @PathVariable UUID id,
            @Valid @RequestBody SubmitRequest request) {
        return decisions.submit(user.getUserId(), id, request.version(), request.confirmed());
    }

    @PostMapping("/api/auth/activate/identity-attempts/{id}/submit")
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityDecisionResponse submitActivation(
            @PathVariable UUID id, @Valid @RequestBody ActivationSubmitRequest request) {
        return decisions.submit(activationUser(request.activationToken()), id, request.version(), request.confirmed());
    }

    @GetMapping("/api/admin/identity-attempts")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<com.fptufa26.ecomotion.verification.identity.dto.response.IdentityDecisionResponse> pending(
            @AuthenticationPrincipal SecurityUser user, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "SUBMITTED") com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus status) {
        return decisions.pending(user.getUserId(), page, status);
    }

    @GetMapping("/api/admin/identity-attempts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public IdentityAttemptResponse detailAdmin(@AuthenticationPrincipal SecurityUser user, @PathVariable UUID id) {
        return decisions.detail(user.getUserId(), id);
    }

    @GetMapping("/api/admin/identity-attempts/{id}/evidence/{kind}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> evidenceAdmin(@AuthenticationPrincipal SecurityUser user,
            @PathVariable UUID id, @PathVariable String kind) {
        return download(decisions.evidence(user.getUserId(), id, kind), kind);
    }

    @GetMapping("/api/admin/identity-attempts/{id}/history/{eventId}/evidence/{kind}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> historyEvidenceAdmin(@AuthenticationPrincipal SecurityUser user,
            @PathVariable UUID id, @PathVariable Long eventId, @PathVariable String kind) {
        return download(decisions.historyEvidence(user.getUserId(), id, eventId, kind), kind);
    }

    @PostMapping("/api/admin/identity-attempts/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityDecisionResponse review(
            @AuthenticationPrincipal SecurityUser user, @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        var detail = decisions.detail(user.getUserId(), id);
        if (detail.role() != com.fptufa26.ecomotion.user.enums.Role.RENTER) {
            throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                    org.springframework.http.HttpStatus.GONE, "PROVIDER_COMBINED_REVIEW_REQUIRED",
                    "Vui lòng duyệt CCCD/video và thông tin dịch vụ chung tại hồ sơ Provider.");
        }
        return decisions.reviewRenter(user.getUserId(), id, request.version(), request.decision(), request.reasonCode(), request.note());
    }

    @GetMapping("/api/identity/attempts/{id}/progress")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityProcessingResponse progress(
            @AuthenticationPrincipal SecurityUser user, @PathVariable UUID id) {
        return attempts.progress(user.getUserId(), id);
    }

    @PostMapping("/api/auth/activate/identity-attempts/{id}/progress")
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityProcessingResponse activationProgress(
            @PathVariable UUID id, @Valid @RequestBody ActivationRequest request) {
        return attempts.progress(activationUser(request.activationToken()), id);
    }

    @GetMapping("/api/identity/attempts/history")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public java.util.List<IdentityAttemptResponse> history(@AuthenticationPrincipal SecurityUser user,
            @RequestParam(defaultValue = "0") int page) { return attempts.history(user.getUserId(), page); }

    @PostMapping("/api/auth/activate/identity-attempts/history")
    public java.util.List<IdentityAttemptResponse> activationHistory(@Valid @RequestBody ActivationRequest request,
            @RequestParam(defaultValue = "0") int page) { return attempts.history(activationUser(request.activationToken()), page); }

    @GetMapping("/api/identity/attempts/{id}/history/{eventId}/evidence/{kind}")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public ResponseEntity<byte[]> ownHistoryEvidence(@AuthenticationPrincipal SecurityUser user,
            @PathVariable UUID id, @PathVariable Long eventId, @PathVariable String kind) {
        return download(attempts.historyEvidence(user.getUserId(), id, eventId, kind), kind);
    }

    @PostMapping("/api/auth/activate/identity-attempts/{id}/history/{eventId}/evidence/{kind}")
    public ResponseEntity<byte[]> activationHistoryEvidence(@PathVariable UUID id, @PathVariable Long eventId,
            @PathVariable String kind, @Valid @RequestBody ActivationRequest request) {
        return download(attempts.historyEvidence(activationUser(request.activationToken()), id, eventId, kind), kind);
    }

    @PostMapping("/api/identity/attempts")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public IdentityAttemptResponse create(@AuthenticationPrincipal SecurityUser user) {
        return attempts.createOrResume(user.getUserId());
    }

    @GetMapping("/api/identity/attempts/current")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public IdentityAttemptResponse current(@AuthenticationPrincipal SecurityUser user) {
        return attempts.current(user.getUserId());
    }

    @GetMapping("/api/identity/attempts/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public IdentityAttemptResponse get(@AuthenticationPrincipal SecurityUser user, @PathVariable UUID id) {
        return attempts.get(user.getUserId(), id);
    }

    @PostMapping(value = "/api/identity/attempts/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public IdentityAttemptResponse documents(
            @AuthenticationPrincipal SecurityUser user, @PathVariable UUID id,
            @RequestParam long version, @RequestParam MultipartFile cccdFront,
            @RequestParam MultipartFile cccdBack) {
        return attempts.uploadDocuments(user.getUserId(), id, version, cccdFront, cccdBack);
    }

    @PostMapping(value = "/api/identity/attempts/{id}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public IdentityAttemptResponse video(
            @AuthenticationPrincipal SecurityUser user, @PathVariable UUID id,
            @RequestParam long version, @RequestParam MultipartFile video) {
        return attempts.uploadVideo(user.getUserId(), id, version, video);
    }

    @PostMapping("/api/identity/attempts/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public IdentityAttemptResponse cancel(
            @AuthenticationPrincipal SecurityUser user, @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request) {
        return attempts.cancel(user.getUserId(), id, request.version());
    }

    @GetMapping("/api/identity/attempts/{id}/evidence/{kind}")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public ResponseEntity<byte[]> evidence(
            @AuthenticationPrincipal SecurityUser user, @PathVariable UUID id, @PathVariable String kind) {
        return download(attempts.evidence(user.getUserId(), id, kind), kind);
    }

    @PostMapping("/api/auth/activate/identity-attempts")
    public IdentityAttemptResponse createActivation(@Valid @RequestBody ActivationRequest request) {
        return attempts.createOrResume(activationUser(request.activationToken()));
    }

    @PostMapping("/api/auth/activate/identity-attempts/current")
    public IdentityAttemptResponse currentActivation(@Valid @RequestBody ActivationRequest request) {
        return attempts.current(activationUser(request.activationToken()));
    }

    @PostMapping("/api/auth/activate/identity-attempts/{id}/status")
    public IdentityAttemptResponse getActivation(
            @PathVariable UUID id, @Valid @RequestBody ActivationRequest request) {
        return attempts.get(activationUser(request.activationToken()), id);
    }

    @PostMapping(value = "/api/auth/activate/identity-attempts/{id}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentityAttemptResponse documentsActivation(
            @PathVariable UUID id, @RequestParam String activationToken, @RequestParam long version,
            @RequestParam MultipartFile cccdFront, @RequestParam MultipartFile cccdBack) {
        return attempts.uploadDocuments(activationUser(activationToken), id, version, cccdFront, cccdBack);
    }

    @PostMapping(value = "/api/auth/activate/identity-attempts/{id}/video",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentityAttemptResponse videoActivation(
            @PathVariable UUID id, @RequestParam String activationToken, @RequestParam long version,
            @RequestParam MultipartFile video) {
        return attempts.uploadVideo(activationUser(activationToken), id, version, video);
    }

    @PostMapping("/api/auth/activate/identity-attempts/{id}/cancel")
    public IdentityAttemptResponse cancelActivation(
            @PathVariable UUID id, @Valid @RequestBody ActivationVersionRequest request) {
        return attempts.cancel(activationUser(request.activationToken()), id, request.version());
    }

    @PostMapping("/api/auth/activate/identity-attempts/{id}/evidence/{kind}")
    public ResponseEntity<byte[]> evidenceActivation(
            @PathVariable UUID id, @PathVariable String kind, @Valid @RequestBody ActivationRequest request) {
        return download(attempts.evidence(activationUser(request.activationToken()), id, kind), kind);
    }

    private Long activationUser(String token) {
        var principal = tokens.verifyActivation(token);
        var user = users.findById(principal.userId()).orElseThrow(ActivationTokenInvalidException::new);
        if (user.getStatus() != UserStatus.PENDING || user.getRole() != principal.role()
                || !user.getEmail().equals(principal.email())) {
            throw new ActivationTokenInvalidException();
        }
        return user.getId();
    }

    private ResponseEntity<byte[]> download(byte[] content, String kind) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("identity-" + kind + ".bin").build().toString())
                .body(content);
    }

    public record VersionRequest(@NotNull @Min(0) Long version) {
    }

    public record SubmitRequest(@NotNull @Min(0) Long version, boolean confirmed) { }
    public record ActivationSubmitRequest(@NotBlank String activationToken, @NotNull @Min(0) Long version,
            boolean confirmed) { }
    public record ReviewRequest(@NotNull @Min(0) Long version,
            @NotBlank @io.swagger.v3.oas.annotations.media.Schema(allowableValues={"APPROVE","NEEDS_CHANGES","REJECT","REOPEN"}) String decision,
            String reasonCode, @jakarta.validation.constraints.Size(max = 1000) String note) { }

    public record ActivationRequest(@NotBlank String activationToken) {
    }

    public record ActivationVersionRequest(@NotBlank String activationToken, @NotNull @Min(0) Long version) {
    }
}
