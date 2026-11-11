package com.fptufa26.ecomotion.verification.identity.controller;

import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityLinkResponse;
import com.fptufa26.ecomotion.verification.identity.service.IdentityAccountLinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class IdentityAccountLinkController {
    private final IdentityAccountLinkService links;
    private final TokenService tokens;
    private final UserService users;

    @PostMapping("/api/identity/links")
    @PreAuthorize("hasAnyRole('RENTER','PROVIDER')")
    public IdentityLinkResponse request(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody LinkRequest request) {
        return links.request(user.getUserId(), request.password(), request.targetEmail());
    }

    @GetMapping("/api/identity/links")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER','ROLE_PROVIDER','ROLE_PROVIDER_ONBOARDING')")
    public List<IdentityLinkResponse> list(@AuthenticationPrincipal SecurityUser user, @RequestParam(defaultValue = "0") int page) {
        return links.list(user.getUserId(), page);
    }

    @PostMapping("/api/identity/links/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER','ROLE_PROVIDER','ROLE_PROVIDER_ONBOARDING')")
    public IdentityLinkResponse confirm(@AuthenticationPrincipal SecurityUser user, @PathVariable UUID id,
            @Valid @RequestBody Confirmation request) {
        return links.confirm(user.getUserId(), id, request.version(), request.password());
    }

    @PostMapping("/api/identity/links/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER','ROLE_PROVIDER','ROLE_PROVIDER_ONBOARDING')")
    public IdentityLinkResponse cancel(@AuthenticationPrincipal SecurityUser user, @PathVariable UUID id,
            @Valid @RequestBody Version request) {
        return links.cancel(user.getUserId(), id, request.version());
    }

    @PostMapping("/api/auth/activate/identity-links/current")
    public List<IdentityLinkResponse> listActivation(@Valid @RequestBody Activation request) {
        return links.list(activation(request.activationToken()), request.page());
    }

    @PostMapping("/api/auth/activate/identity-links/{id}/confirm")
    public IdentityLinkResponse confirmActivation(@PathVariable UUID id, @Valid @RequestBody ActivationConfirmation request) {
        return links.confirm(activation(request.activationToken()), id, request.version(), request.password());
    }

    @PostMapping("/api/auth/activate/identity-links/{id}/cancel")
    public IdentityLinkResponse cancelActivation(@PathVariable UUID id, @Valid @RequestBody ActivationVersion request) {
        return links.cancel(activation(request.activationToken()), id, request.version());
    }

    private Long activation(String value) {
        var principal = tokens.verifyActivation(value);
        var user = users.findById(principal.userId()).orElseThrow(ActivationTokenInvalidException::new);
        if (user.getStatus() != UserStatus.PENDING || user.getRole() != principal.role()
                || !user.getEmail().equals(principal.email())) throw new ActivationTokenInvalidException();
        return user.getId();
    }

    public record LinkRequest(@NotBlank @Size(max=128) String password, @NotBlank @Email @Size(max=255) String targetEmail) { }
    public record Confirmation(@NotNull @Min(0) Long version, @NotBlank @Size(max=128) String password) { }
    public record Version(@NotNull @Min(0) Long version) { }
    public record Activation(@NotBlank String activationToken, @Min(0) int page) { }
    public record ActivationConfirmation(@NotBlank String activationToken, @NotNull @Min(0) Long version,
            @NotBlank @Size(max=128) String password) { }
    public record ActivationVersion(@NotBlank String activationToken, @NotNull @Min(0) Long version) { }
}
