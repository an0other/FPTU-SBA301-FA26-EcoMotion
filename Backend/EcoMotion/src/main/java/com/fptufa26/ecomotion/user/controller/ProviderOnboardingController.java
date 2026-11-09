package com.fptufa26.ecomotion.user.controller;

import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.dto.request.ProviderApplicationRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderApplicationResponse;
import com.fptufa26.ecomotion.user.service.ProviderOnboardingService;
import com.fptufa26.ecomotion.auth.service.ActivationService;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.auth.dto.request.SendPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.request.VerifyPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.response.SendPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyPhoneOtpResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProviderOnboardingController {
    private final ProviderOnboardingService onboarding;
    private final ActivationService activation;
    private final TokenService tokens;

    @GetMapping("/api/provider/onboarding/options")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public List<BankOption> options() {
        return java.util.Arrays.stream(com.fptufa26.ecomotion.finance.enums.SupportedBank.values())
                .map(bank -> new BankOption(bank.getCode(), bank.getShortName(), bank.getMinAccountLength(), bank.getMaxAccountLength())).toList();
    }

    @GetMapping("/api/provider/onboarding")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public ProviderApplicationResponse get(@AuthenticationPrincipal SecurityUser user) {
        return onboarding.get(user.getUserId());
    }

    @PutMapping("/api/provider/onboarding")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public ProviderApplicationResponse save(@AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody ProviderApplicationRequest request) {
        return onboarding.save(user.getUserId(), request);
    }

    @PostMapping("/api/provider/onboarding/submit")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public ProviderApplicationResponse submit(@AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody VersionRequest request) {
        return onboarding.submit(user.getUserId(), request.version());
    }

    @PostMapping("/api/provider/onboarding/phone/send-otp")
    @PreAuthorize("hasAuthority('ROLE_PROVIDER_ONBOARDING')")
    public SendPhoneOtpResponse sendPhone(@AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody PhoneRequest request) {
        String token = tokens.issueActivation(user.getUserId(), user.getUsername(), user.getRole());
        return activation.sendPhoneOtp(new SendPhoneOtpRequest(token, request.phone()));
    }

    @PostMapping("/api/provider/onboarding/phone/verify-otp")
    @PreAuthorize("hasAuthority('ROLE_PROVIDER_ONBOARDING')")
    public VerifyPhoneOtpResponse verifyPhone(@AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody OtpRequest request) {
        String token = tokens.issueActivation(user.getUserId(), user.getUsername(), user.getRole());
        return activation.verifyPhoneOtp(new VerifyPhoneOtpRequest(token, request.otp()));
    }

    @GetMapping("/api/admin/provider-applications")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProviderApplicationResponse> pending(@AuthenticationPrincipal SecurityUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "SUBMITTED") com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus status) {
        return onboarding.pending(user.getUserId(), page, status);
    }

    @GetMapping("/api/admin/provider-applications/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProviderApplicationResponse detail(@AuthenticationPrincipal SecurityUser user, @PathVariable Long userId) {
        return onboarding.adminGet(user.getUserId(), userId);
    }

    @PostMapping("/api/admin/provider-applications/{userId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ProviderApplicationResponse review(@AuthenticationPrincipal SecurityUser user, @PathVariable Long userId,
            @Valid @RequestBody ReviewRequest request) {
        return onboarding.review(user.getUserId(), userId, request.version(), request.decision(), request.reasonCode());
    }

    @GetMapping("/api/admin/provider-applications/{userId}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReviewHistory> history(@AuthenticationPrincipal SecurityUser user, @PathVariable Long userId) {
        return onboarding.history(user.getUserId(), userId).stream().map(review -> new ReviewHistory(
                review.getApplicationVersion(), review.getAdminId(), review.getDecision(), review.getReasonCode(), review.getReviewedAt())).toList();
    }

    public record VersionRequest(@NotNull @Min(0) Long version) { }
    public record BankOption(String code, String name, int minimumAccountLength, int maximumAccountLength) { }
    public record ReviewRequest(@NotNull @Min(0) Long version,
            @NotBlank @io.swagger.v3.oas.annotations.media.Schema(allowableValues={"APPROVE","NEEDS_CHANGES","REJECT","REOPEN"}) String decision,
            String reasonCode) { }
    public record PhoneRequest(@NotBlank @Pattern(regexp = "^0[0-9]{9,10}$") String phone) { }
    public record OtpRequest(@NotBlank String otp) { }
    public record ReviewHistory(Long version, Long adminId, String decision, String reasonCode, java.time.Instant reviewedAt) { }
}
